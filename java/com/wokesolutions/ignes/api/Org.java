package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.ApplicationData;
import com.wokesolutions.ignes.data.TaskData;
import com.wokesolutions.ignes.data.WorkerRegisterData;
import com.wokesolutions.ignes.exceptions.UserNotWorkerException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Prop;
import com.wokesolutions.ignes.util.Storage;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/org")
public class Org {

	private static final Logger LOG = Logger.getLogger(Org.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final int BATCH_SIZE = 10;

	@POST
	@Path("/registerworker")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerWorker(WorkerRegisterData registerData,
			@Context HttpServletRequest request) {
		if(!registerData.isValid()) {
			LOG.info(Message.REGISTER_DATA_INVALID);
			return Response.status(Status.BAD_REQUEST).build();
		}

		String org = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		if(org == null)
			return Response.status(Status.EXPECTATION_FAILED).build();

		int retries = 5;
		while(true) {
			try {
				return registerWorkerRetry(registerData, org);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response registerWorkerRetry(WorkerRegisterData registerData, String org) {
		LOG.info(Message.ATTEMPT_REGISTER_WORKER + registerData.name);

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		try {
			String email = registerData.email;

			Filter emailFilter =
					new Query.FilterPredicate(DSUtils.USER_EMAIL, FilterOperator.EQUAL, email);

			Query userQuery = new Query(DSUtils.USER)
					.setFilter(emailFilter);

			Entity userResult;

			try {
				userResult = datastore.prepare(userQuery).asSingleEntity();
			} catch(TooManyResultsException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(userResult != null) {
				LOG.info(Message.USER_ALREADY_EXISTS);
				txn.rollback();
				return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build();
			}
			
			Key orgKey = KeyFactory.createKey(DSUtils.USER, org);

			try {
				datastore.get(orgKey);
			} catch(EntityNotFoundException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			Date date = new Date();

			
			LOG.info(org);
			Entity user = new Entity(DSUtils.USER, email);
			Key userK = user.getKey();
			Entity worker = new Entity(DSUtils.WORKER, userK);

			String pw = WorkerRegisterData.generateCode(org, email);
			String pwSha = DigestUtils.sha512Hex(pw);
			worker.setProperty(DSUtils.WORKER_ORG, org);
			worker.setProperty(DSUtils.WORKER_JOB, registerData.job);
			worker.setProperty(DSUtils.WORKER_NAME, registerData.name);
			worker.setUnindexedProperty(DSUtils.WORKER_CREATIONTIME, date);
			
			String orgName;
			Query orgQ;

			orgQ = new Query(DSUtils.ORG).setAncestor(orgKey)
					.addProjection(new PropertyProjection(DSUtils.ORG_NAME, String.class));
			
			Entity orgE;
			try {
				orgE = datastore.prepare(orgQ).asSingleEntity();
				
				if(orgE == null) {
					LOG.info(Message.UNEXPECTED_ERROR);
					txn.rollback();
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
				
				orgName = orgE.getProperty(DSUtils.ORG_NAME).toString();
			} catch(TooManyResultsException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			
			worker.setUnindexedProperty(DSUtils.WORKER_ORGNAME, orgName);

			user.setUnindexedProperty(DSUtils.USER_PASSWORD, pwSha);
			user.setProperty(DSUtils.USER_EMAIL, email);
			user.setProperty(DSUtils.USER_LEVEL, UserLevel.WORKER);
			user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);

			Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
			userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);

			List<Entity> list = Arrays.asList(user, worker, userPoints);
			
			Email.sendWorkerRegisterMessage(email, pw,
					orgE.getProperty(DSUtils.ORG_NAME).toString());
			
			datastore.put(txn, list);
			txn.commit();
			LOG.info(Message.WORKER_REGISTERED + registerData.email);
			return Response.ok().build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@DELETE
	@Path("/deleteworker/{email}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response deleteWorker(@PathParam(ParamName.EMAIL) String email,
			@Context HttpServletRequest request) {
		if(!isValid(email))
			return Response.status(Status.BAD_REQUEST).build();

		String org = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		if(org == null) {
			return Response.status(Status.EXPECTATION_FAILED).build();
		}

		int retries = 5;
		while(true) {
			try {
				return deleteWorkerRetry(email, org);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	public Response deleteWorkerRetry(String email, String org) {
		Key userKey = KeyFactory.createKey(DSUtils.USER, email);
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);

		try {
			Entity user = datastore.get(userKey);
			Entity worker;

			try {
				Query query = new Query(DSUtils.WORKER).setAncestor(userKey);
				worker = datastore.prepare(query).asSingleEntity();
			} catch(TooManyResultsException e) {
				txn.rollback();
				LOG.info(Message.WORKER_NOT_FOUND);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(worker == null) {
				txn.rollback();
				LOG.info(Message.WORKER_NOT_FOUND);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(!worker.getProperty(DSUtils.WORKER_ORG).toString().equals(org)) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}

			Entity deletedWorker = new Entity(DSUtils.DELETEDWORKER, email);
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_CREATIONTIME,
					worker.getProperty(DSUtils.WORKER_CREATIONTIME));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_JOB,
					worker.getProperty(DSUtils.WORKER_JOB));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_ORG, org);

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_PASSWORD,
					user.getProperty(DSUtils.USER_PASSWORD));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_DELETIONTIME, new Date());

			LOG.info(userKey.toString());

			List<Key> list = Arrays.asList(userKey, worker.getKey());

			datastore.delete(txn, list);

			Query query = new Query(DSUtils.TOKEN).setAncestor(userKey).setKeysOnly();
			List<Entity> listToken = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
			List<Key> tokens = new ArrayList<Key>(listToken.size());

			for(Entity e : listToken)
				tokens.add(e.getKey());

			datastore.delete(txn, tokens);

			datastore.put(txn, deletedWorker);

			LOG.info(Message.DELETED_WORKER + email);
			txn.commit();
			return Response.ok().build();

		} catch (EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@GET
	@Path("/listworkers")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getWorkers(@Context HttpServletRequest request,
			@QueryParam(ParamName.CURSOR) String cursor) {
		String org = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		if(org == null)
			return Response.status(Status.EXPECTATION_FAILED).build();

		int retries = 5;
		while(true) {
			try {
				return getWorkersRetry(org, cursor);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getWorkersRetry(String org, String cursor) {
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		Query query = new Query(DSUtils.WORKER);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Filter filter = new Query.FilterPredicate(DSUtils.WORKER_ORG,
				FilterOperator.EQUAL, org);

		query.setFilter(filter);

		query.addProjection(new PropertyProjection(DSUtils.WORKER_JOB, String.class))
		.addProjection(new PropertyProjection(DSUtils.WORKER_NAME, String.class));

		QueryResultList<Entity> list = datastore.prepare(query).asQueryResultList(fetchOptions);

		JSONArray array = new JSONArray();

		for(Entity worker : list) {
			JSONObject obj = new JSONObject();
			obj.put(Prop.EMAIL, worker.getParent().getName());
			obj.put(Prop.NAME, worker.getProperty(DSUtils.WORKER_NAME));
			obj.put(Prop.JOB, worker.getProperty(DSUtils.WORKER_JOB));
			array.put(obj);
		}

		if(array.length() < BATCH_SIZE)
			return Response.ok(array.toString()).build();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR,
				list.getCursor().toWebSafeString()).build();
	}

	private boolean isValid(String email) {
		if(email == null)
			return false;
		if(email == "")
			return false;
		if(!email.contains("@"))
			return false;
		return true;
	}

	@POST
	@Path("/givetask")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response giveTask(TaskData data, @Context HttpServletRequest request) {
		if(!data.isValid())
			return Response.status(Status.BAD_REQUEST).build();

		String org = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				return giveTaskRetry(data, org);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response giveTaskRetry(TaskData data, String org) {
		String report = data.report;
		String email = data.email;
		String indications = data.indications;

		Entity reportE;

		Query workerQuery = new Query(DSUtils.WORKER)
				.setAncestor(KeyFactory.createKey(DSUtils.USER, email));
		workerQuery.addProjection(new PropertyProjection(DSUtils.WORKER_ORG, String.class));

		Entity worker;

		try {
			worker = datastore.prepare(workerQuery).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		if(worker == null) {
			LOG.info(Message.WORKER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		if(!worker.getProperty(DSUtils.WORKER_ORG).toString().equals(org)) {
			LOG.info(Message.WORKER_NOT_FOUND);
			return Response.status(Status.FORBIDDEN).build();
		}

		try {
			reportE = datastore.get(KeyFactory.createKey(DSUtils.REPORT, report));
		} catch (EntityNotFoundException e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		try {
			Entity user = datastore.get(KeyFactory.createKey(DSUtils.USER, email));
			if(!user.getProperty(DSUtils.USER_LEVEL).equals(UserLevel.WORKER))
				throw new UserNotWorkerException();
		} catch (EntityNotFoundException e) {
			LOG.info(Message.WORKER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		} catch(UserNotWorkerException e2) {
			LOG.info(e2.getMessage());
			return Response.status(Status.NOT_FOUND).build();
		}

		Query query = new Query(DSUtils.TASK).setAncestor(KeyFactory.createKey(DSUtils.REPORT, report));
		Filter filter = new Query.FilterPredicate(DSUtils.TASK_WORKER, FilterOperator.EQUAL, email);
		query.setFilter(filter);

		try {
			Entity existingTask = datastore.prepare(query).asSingleEntity();
			if(existingTask != null) {
				LOG.info(Message.DUPLICATED_TASK);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		Date date = new Date();

		Entity task = new Entity(DSUtils.TASK, reportE.getKey());

		task.setProperty(DSUtils.TASK_WORKER, email);
		task.setProperty(DSUtils.TASK_TIME, date);
		task.setProperty(DSUtils.TASK_TIMEFORMATTED,
				new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(date));
		task.setProperty(DSUtils.TASK_ORG, org);

		if(indications != null && !indications.equals(""))
			task.setProperty(DSUtils.TASK_INDICATIONS, indications);

		datastore.put(task);

		return Response.ok().build();
	}

	@GET
	@Path("/info/{nif}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getInfo(@PathParam(ParamName.NIF) String nif) {
		int retries = 5;

		while(true) {
			try {
				Entity user;

				try {
					user = datastore.get(KeyFactory.createKey(DSUtils.USER, nif));
				} catch(EntityNotFoundException e) {
					LOG.info(Message.ORG_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}
				
				Entity org;
				try {
					Query orgQ = new Query(DSUtils.ORG).setAncestor(user.getKey());
					org = datastore.prepare(orgQ).asSingleEntity();
					
					if(org == null) {
						LOG.info(Message.ORG_NOT_FOUND);
						return Response.status(Status.NOT_FOUND).build();
					}
				} catch (TooManyResultsException e) {
					LOG.info(Message.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				JSONObject obj = new JSONObject();
				obj.put(Prop.NIF, org.getParent().getName());
				obj.put(Prop.ADDRESS, org.getProperty(DSUtils.ORG_ADDRESS));
				obj.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
				obj.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));
				obj.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE));
				obj.put(Prop.SERVICES, org.getProperty(DSUtils.ORG_SERVICES));
				obj.put(Prop.ZIP, org.getProperty(DSUtils.ORG_ZIP));
				obj.put(Prop.LOCALITY, org.getProperty(DSUtils.ORG_LOCALITY));

				return Response.ok(obj.toString()).build();
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	@GET
	@Path("/alltasks")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response allTasks(@Context HttpServletRequest request,
			@QueryParam(ParamName.CURSOR) String cursor) {
		String org = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

				if(cursor != null && !cursor.equals(""))
					fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

				Query query = new Query(DSUtils.TASK);
				Filter filter = new Query.FilterPredicate(DSUtils.TASK_ORG, FilterOperator.EQUAL, org);

				QueryResultList<Entity> list = datastore.prepare(query.setFilter(filter))
						.asQueryResultList(fetchOptions);

				JSONArray array = new JSONArray();

				for(Entity task : list) {
					Entity report;
					try {
						report = datastore.get(task.getParent());
					} catch (EntityNotFoundException e) {
						LOG.info(Message.UNEXPECTED_ERROR);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					JSONObject jsonReport = new JSONObject();

					jsonReport.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
					jsonReport.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
					jsonReport.put(Prop.USERNAME,
							((Key) report.getProperty(DSUtils.REPORT_USER)).getName());
					jsonReport.put(Prop.GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
					jsonReport.put(Prop.STATUS, report.getProperty(DSUtils.REPORT_STATUS));
					jsonReport.put(Prop.DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION));
					jsonReport.put(Prop.CREATIONTIME,
							report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
					jsonReport.put(Prop.ISPRIVATE, report.getProperty(DSUtils.REPORT_PRIVATE));
					jsonReport.put(Prop.TASK, task.getParent().getName());
					jsonReport.put(Prop.WORKER, task.getProperty(DSUtils.TASK_WORKER));
					jsonReport.put(Prop.INDICATIONS, task.getProperty(DSUtils.TASK_INDICATIONS));
					jsonReport.put(Prop.TASK_TIME, task.getProperty(DSUtils.TASK_TIME));

					String tn = Storage.getImage(report.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());
					jsonReport.put(Prop.THUMBNAIL, tn);

					array.put(jsonReport);
				}

				if(array.length() < BATCH_SIZE)
					return Response.ok(array.toString()).build();

				cursor = list.getCursor().toWebSafeString();

				return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();

			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}
	
	@POST
	@Path("/apply/{report}")
	public Response apply(@Context HttpServletRequest request,
			@PathParam(ParamName.REPORT) String reportid, ApplicationData data) {
		int retries = 5;
		while(true) {
			try {
				return applyRetry(request, reportid, data);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}
	
	private Response applyRetry(HttpServletRequest request, String reportid,
			ApplicationData data) {
		String orgnif = request.getAttribute(CustomHeader.USERNAME_ATT).toString();
		
		Key reportK = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Entity report;
		try {
			report = datastore.get(reportK);
		} catch(EntityNotFoundException e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}
		
		Query taskQ = new Query(DSUtils.TASK).setAncestor(reportK);
		Entity task;
		try {
			task = datastore.prepare(taskQ).asSingleEntity();
			
			if(task != null) {
				LOG.info(Message.TASK_ALREADY_ASSIGNED);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		Key userK = KeyFactory.createKey(DSUtils.USER, orgnif);
		
		Entity org;
		Query orgQ = new Query(DSUtils.ORG).setAncestor(userK);
		try {
			org = datastore.prepare(orgQ).asSingleEntity();
			
			if(org == null) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		Key orgK = org.getKey();
		
		boolean orgprivate = (boolean) org.getProperty(DSUtils.ORG_PRIVATE);
		boolean reportprivate = (boolean) report.getProperty(DSUtils.REPORT_PRIVATE);
		
		if(!orgprivate && reportprivate) {
			LOG.info(Message.REPORT_IS_PRIVATE);
			return Response.status(Status.FORBIDDEN).build();
		}
		
		Date date = new Date();
		
		Entity application = new Entity(DSUtils.APPLICATION);
		application.setProperty(DSUtils.APPLICATION_BUGDET, data.bugdet);
		application.setProperty(DSUtils.APPLICATION_TIME, date);
		application.setProperty(DSUtils.APPLICATION_FORMATTEDTIME,
				new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(date));
		application.setProperty(DSUtils.APPLICATION_INFO, data.info);
		application.setProperty(DSUtils.APPLICATION_ORG, orgK);
		application.setProperty(DSUtils.APPLICATION_REPORT, reportK);
		
		datastore.put(application);
		return Response.ok().build();
	}
}
