package com.wokesolutions.ignes.api;

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
import com.wokesolutions.ignes.data.TaskData;
import com.wokesolutions.ignes.data.WorkerRegisterData;
import com.wokesolutions.ignes.exceptions.UserNotWorkerException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
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
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

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
		LOG.info(Message.ATTEMPT_REGISTER_WORKER + registerData.worker_name);

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		try {
			String email = registerData.worker_email;

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

			Key orgKey = KeyFactory.createKey(DSUtils.ORG, org);

			if(userResult != null) {
				txn.rollback();
				return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build();
			}

			Date date = new Date();

			Entity user = new Entity(DSUtils.USER, email);
			Key userKey = user.getKey();
			Entity worker = new Entity(DSUtils.WORKER, userKey);

			String pw = WorkerRegisterData.generateCode(org, email);
			String pwSha = DigestUtils.sha512Hex(pw);
			worker.setProperty(DSUtils.WORKER_ORG, org);
			worker.setProperty(DSUtils.WORKER_JOB, registerData.worker_job);
			worker.setProperty(DSUtils.WORKER_NAME, registerData.worker_name);
			worker.setUnindexedProperty(DSUtils.WORKER_CREATIONTIME, date);

			user.setUnindexedProperty(DSUtils.USER_PASSWORD, pwSha);
			user.setProperty(DSUtils.USER_EMAIL, email);
			user.setProperty(DSUtils.USER_LEVEL, UserLevel.WORKER);
			user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);

			Entity userPoints = new Entity(DSUtils.USERPOINTS, user.getKey());
			userPoints.setProperty(DSUtils.USERPOINTS_POINTS, 0);

			Entity useroptional = new Entity(DSUtils.USEROPTIONAL, userKey);

			List<Entity> list = Arrays.asList(user, worker, userPoints, useroptional);

			try {
				Entity orgEntity = datastore.get(orgKey);
				Email.sendWorkerRegisterMessage(email, pw,
						orgEntity.getProperty(DSUtils.ORG_NAME).toString());
			} catch(EntityNotFoundException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
			datastore.put(txn, list);
			txn.commit();
			LOG.info(Message.WORKER_REGISTERED + registerData.worker_email);
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
		LOG.info("listing workers");

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
			obj.put(DSUtils.WORKER, worker.getParent().getName());
			obj.put(DSUtils.WORKER_NAME, worker.getProperty(DSUtils.WORKER_NAME));
			obj.put(DSUtils.WORKER_JOB, worker.getProperty(DSUtils.WORKER_JOB));
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

		LOG.info("1");

		Entity reportE;

		LOG.info("2");

		Query workerQuery = new Query(DSUtils.WORKER)
				.setAncestor(KeyFactory.createKey(DSUtils.USER, email));
		workerQuery.addProjection(new PropertyProjection(DSUtils.WORKER_ORG, String.class));

		LOG.info("3");

		Entity worker;

		try {
			worker = datastore.prepare(workerQuery).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		LOG.info("4");

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

		Entity task = new Entity(DSUtils.TASK, reportE.getKey());

		task.setProperty(DSUtils.TASK_WORKER, email);
		task.setProperty(DSUtils.TASK_TIME, new Date());
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
				Entity org;

				try {
					org = datastore.get(KeyFactory.createKey(DSUtils.ORG, nif));
				} catch(EntityNotFoundException e) {
					LOG.info(Message.ORG_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				JSONObject obj = new JSONObject();
				obj.put(DSUtils.ORG, org.getKey().getName());
				obj.put(DSUtils.ORG_ADDRESS, org.getProperty(DSUtils.ORG_ADDRESS));
				obj.put(DSUtils.ORG_EMAIL, org.getProperty(DSUtils.ORG_EMAIL));
				obj.put(DSUtils.ORG_NAME, org.getProperty(DSUtils.ORG_NAME));
				obj.put(DSUtils.ORG_PHONE, org.getProperty(DSUtils.ORG_PHONE));
				obj.put(DSUtils.ORG_SERVICES, org.getProperty(DSUtils.ORG_SERVICES));
				obj.put(DSUtils.ORG_ZIP, org.getProperty(DSUtils.ORG_ZIP));
				obj.put(DSUtils.ORG_LOCALITY, org.getProperty(DSUtils.ORG_LOCALITY));

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

					jsonReport.put(DSUtils.REPORT_TITLE, report.getProperty(DSUtils.REPORT_TITLE));
					jsonReport.put(DSUtils.REPORT_ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
					jsonReport.put(DSUtils.REPORT_USERNAME, report.getProperty(DSUtils.REPORT_USERNAME));
					jsonReport.put(DSUtils.REPORT_GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
					jsonReport.put(DSUtils.REPORT_STATUS, report.getProperty(DSUtils.REPORT_STATUS));
					jsonReport.put(DSUtils.REPORT_DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION));
					jsonReport.put(DSUtils.REPORT_CREATIONTIMEFORMATTED,
							report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
					jsonReport.put(DSUtils.REPORT_PRIVATE, report.getProperty(DSUtils.REPORT_PRIVATE));
					jsonReport.put(DSUtils.TASK, task.getParent().getName());
					jsonReport.put(DSUtils.TASK_WORKER, task.getProperty(DSUtils.TASK_WORKER));
					jsonReport.put(DSUtils.TASK_INDICATIONS, task.getProperty(DSUtils.TASK_INDICATIONS));

					String tn = Storage.getImage(report.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());
					jsonReport.put(DSUtils.REPORT_THUMBNAIL, tn);

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
}
