package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
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
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Prop;
import com.wokesolutions.ignes.util.Storage;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/org")
public class Org {

	private static final Logger LOG = Logger.getLogger(Org.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final int BATCH_SIZE = 10;
	private static final int BATCH_SIZE_BIG = 20;

	@POST
	@Path("/registerworker")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerWorker(WorkerRegisterData registerData,
			@Context HttpServletRequest request) {
		if(!registerData.isValid()) {
			LOG.info(Log.REGISTER_DATA_INVALID);
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
		LOG.info(Log.ATTEMPT_REGISTER_WORKER + registerData.name);

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
				LOG.info(Log.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(userResult != null) {
				LOG.info(Log.USER_ALREADY_EXISTS);
				txn.rollback();
				return Response.status(Status.CONFLICT).entity(Log.USER_ALREADY_EXISTS).build();
			}

			Key orgUK = KeyFactory.createKey(DSUtils.USER, org);
			Key orgK = KeyFactory.createKey(orgUK, DSUtils.ORG, org);

			try {
				datastore.get(orgK);
			} catch(EntityNotFoundException e) {
				LOG.info(Log.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			Date date = new Date();


			LOG.info(org);
			Entity user = new Entity(DSUtils.USER, email);
			Key userK = user.getKey();
			Entity worker = new Entity(DSUtils.WORKER, email, userK);

			String pw = WorkerRegisterData.generateCode(org, email);
			String pwSha = DigestUtils.sha512Hex(pw);
			worker.setProperty(DSUtils.WORKER_ORG, orgK);
			worker.setProperty(DSUtils.WORKER_JOB, registerData.job);
			worker.setProperty(DSUtils.WORKER_NAME, registerData.name);
			worker.setUnindexedProperty(DSUtils.WORKER_CREATIONTIME, date);

			String orgName;
			Query orgQ;

			orgQ = new Query(DSUtils.ORG).setAncestor(orgK)
					.addProjection(new PropertyProjection(DSUtils.ORG_NAME, String.class));

			Entity orgE;
			try {
				orgE = datastore.prepare(orgQ).asSingleEntity();

				if(orgE == null) {
					LOG.info(Log.UNEXPECTED_ERROR);
					txn.rollback();
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				orgName = orgE.getProperty(DSUtils.ORG_NAME).toString();
			} catch(TooManyResultsException e) {
				LOG.info(Log.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			worker.setUnindexedProperty(DSUtils.WORKER_ORGNAME, orgName);

			user.setUnindexedProperty(DSUtils.USER_PASSWORD, pwSha);
			user.setProperty(DSUtils.USER_EMAIL, email);
			user.setProperty(DSUtils.USER_LEVEL, UserLevel.WORKER);
			user.setUnindexedProperty(DSUtils.USER_CREATIONTIME, date);

			Entity userStats = new Entity(DSUtils.USERSTATS, email, userK);
			userStats.setProperty(DSUtils.USERSTATS_LOGINS, 0L);
			userStats.setProperty(DSUtils.USERSTATS_LOGINSFAILED, 0L);
			userStats.setProperty(DSUtils.USERSTATS_LOGOUTS, 0L);
			
			List<Entity> list = Arrays.asList(user, worker, userStats);

			Email.sendWorkerRegisterMessage(email, pw,
					orgE.getProperty(DSUtils.ORG_NAME).toString());

			datastore.put(txn, list);
			txn.commit();
			LOG.info(Log.WORKER_REGISTERED + registerData.email);
			return Response.ok().build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Log.TXN_ACTIVE);
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

			Key orgUK = KeyFactory.createKey(DSUtils.USER, org);
			Key orgK = KeyFactory.createKey(orgUK, DSUtils.ORG, org);

			try {
				Query query = new Query(DSUtils.WORKER).setAncestor(userKey);
				worker = datastore.prepare(query).asSingleEntity();
			} catch(TooManyResultsException e) {
				txn.rollback();
				LOG.info(Log.WORKER_NOT_FOUND);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(worker == null) {
				txn.rollback();
				LOG.info(Log.WORKER_NOT_FOUND);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(!worker.getProperty(DSUtils.WORKER_ORG).equals(orgK)) {
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

			Filter tokenF = new Query.FilterPredicate(DSUtils.TOKEN_USER,
					FilterOperator.EQUAL, userKey);
			Query tokenQ = new Query(DSUtils.TOKEN).setKeysOnly().setFilter(tokenF);
			List<Entity> listToken = datastore.prepare(tokenQ)
					.asList(FetchOptions.Builder.withDefaults());
			List<Key> tokens = new ArrayList<Key>(listToken.size());

			for(Entity e : listToken)
				tokens.add(e.getKey());

			datastore.delete(txn, tokens);

			Filter deviceF = new Query.FilterPredicate(DSUtils.DEVICE_USER,
					FilterOperator.EQUAL, userKey);
			Query deviceQ = new Query(DSUtils.DEVICE).setAncestor(userKey).setFilter(deviceF);
			List<Entity> devicelist = datastore.prepare(deviceQ)
					.asList(FetchOptions.Builder.withDefaults());
			List<Key> devices = new ArrayList<Key>(devicelist.size());

			for(Entity e : devicelist)
				devices.add(e.getKey());

			datastore.delete(txn, devices);

			datastore.put(txn, deletedWorker);

			LOG.info(Log.DELETED_WORKER + email);
			txn.commit();
			return Response.ok().build();

		} catch (EntityNotFoundException e) {
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Log.TXN_ACTIVE);
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

		Key userK = KeyFactory.createKey(DSUtils.USER, org);
		Key orgK = KeyFactory.createKey(userK, DSUtils.ORG, org);

		Filter filter = new Query.FilterPredicate(DSUtils.WORKER_ORG,
				FilterOperator.EQUAL, orgK);

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

	private Response giveTaskRetry(TaskData data, String orgnif) {
		String reportid = data.report;

		Key userK = KeyFactory.createKey(DSUtils.USER, orgnif);
		Key orgK = KeyFactory.createKey(userK, DSUtils.ORG, orgnif);

		Key workerUK = KeyFactory.createKey(DSUtils.USER, data.email);
		Key workerK = KeyFactory.createKey(workerUK, DSUtils.WORKER, data.email);

		Key reportK = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Key orgtaskK = KeyFactory.createKey(reportK, DSUtils.ORGTASK, reportid);

		Entity worker;
		try {
			worker = datastore.get(workerK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.WORKER_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		if(!worker.getProperty(DSUtils.WORKER_ORG).equals(orgK)) {
			LOG.info(Log.WORKER_NOT_FOUND);
			return Response.status(Status.FORBIDDEN).build();
		}

		Entity orgtask;
		try {
			orgtask = datastore.get(orgtaskK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.TASK_NOT_FOUND);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}

		if(!orgtask.getProperty(DSUtils.ORGTASK_ORG).equals(orgK)) {
			LOG.info(Log.TASK_NOT_FOUND);
			return Response.status(Status.FORBIDDEN).build();
		}

		Entity task = new Entity(DSUtils.TASK, reportK.getName(), orgtaskK);
		task.setProperty(DSUtils.TASK_WORKER, workerK);

		if(data.indications != null && !data.indications.equals(""))
			task.setProperty(DSUtils.TASK_INDICATIONS, data.indications);
		
		task.setProperty(DSUtils.TASK_TIME, new Date());

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
					LOG.info(Log.ORG_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				Entity org;
				try {
					Query orgQ = new Query(DSUtils.ORG).setAncestor(user.getKey());
					org = datastore.prepare(orgQ).asSingleEntity();

					if(org == null) {
						LOG.info(Log.ORG_NOT_FOUND);
						return Response.status(Status.NOT_FOUND).build();
					}
				} catch (TooManyResultsException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				JSONObject obj = new JSONObject();
				obj.put(Prop.NIF, org.getParent().getName());
				obj.put(Prop.ADDRESS, org.getProperty(DSUtils.ORG_ADDRESS));
				obj.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
				obj.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));
				obj.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE));
				obj.put(Prop.SERVICES, org.getProperty(DSUtils.ORG_CATEGORIES));
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
		String nif = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				Key userK = KeyFactory.createKey(DSUtils.USER, nif);
				Query orgQ = new Query(DSUtils.ORG).setAncestor(userK).setKeysOnly();
				Entity org;
				try {
					org = datastore.prepare(orgQ).asSingleEntity();

					if(org == null) {
						LOG.info(Log.UNEXPECTED_ERROR);
						return Response.status(Status.NOT_FOUND).build();
					}
				} catch(TooManyResultsException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.NOT_FOUND).build();
				}

				Key orgK = org.getKey();

				FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

				if(cursor != null && !cursor.equals(""))
					fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

				Query query = new Query(DSUtils.TASK);
				Filter filter = new Query.FilterPredicate(DSUtils.TASK_ORG, 
						FilterOperator.EQUAL, orgK);

				QueryResultList<Entity> list = datastore.prepare(query.setFilter(filter))
						.asQueryResultList(fetchOptions);

				JSONArray array = new JSONArray();

				for(Entity task : list) {
					Entity report;
					try {
						report = datastore.get(task.getParent());
					} catch (EntityNotFoundException e) {
						LOG.info(Log.UNEXPECTED_ERROR);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					Query workertaskQ = new Query(DSUtils.TASK).setAncestor(task.getKey());
					List<Entity> workertasks = datastore.prepare(workertaskQ)
							.asList(FetchOptions.Builder.withDefaults());

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
					jsonReport.put(Prop.WORKER,
							((Key) task.getProperty(DSUtils.TASK_WORKER)).getParent().getName());
					jsonReport.put(Prop.INDICATIONS, task.getProperty(DSUtils.TASK_INDICATIONS));
					jsonReport.put(Prop.TASK_TIME, task.getProperty(DSUtils.TASK_TIME));

					String workers = "";
					for(Entity taskW : workertasks)
						workers += taskW.getProperty(DSUtils.TASK_WORKER).toString();

					jsonReport.put(Prop.WORKERS, workers);

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
			LOG.info(Log.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}
		
		if(report.getProperty(DSUtils.REPORT_STATUS).equals(Report.STANDBY)) {
			LOG.info(Log.REPORT_STANDBY);
			return Response.status(Status.FORBIDDEN).build();
		}

		Key userK = KeyFactory.createKey(DSUtils.USER, orgnif);
		Key orgK = KeyFactory.createKey(userK, DSUtils.ORG, orgnif);

		Filter taskF = new Query.FilterPredicate(DSUtils.ORGTASK_ORG,
				FilterOperator.EQUAL, orgK);

		Filter applicationF = new Query.FilterPredicate(DSUtils.APPLICATION_REPORT,
				FilterOperator.EQUAL, reportK);
		Query applicationQ = new Query(DSUtils.APPLICATION)
				.setAncestor(orgK).setFilter(applicationF);
		
		int count = datastore.prepare(applicationQ).countEntities(FetchOptions.Builder.withDefaults());
		if(count > 0) {
			LOG.info(Log.DUPLICATED_TASK);
			return Response.status(Status.CONFLICT).build();
		}
		
		Query taskQ = new Query(DSUtils.ORGTASK).setAncestor(orgK).setFilter(taskF);
		Entity task;
		try {
			task = datastore.prepare(taskQ).asSingleEntity();

			if(task != null) {
				LOG.info(Log.TASK_ALREADY_ASSIGNED);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
		} catch(TooManyResultsException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		Entity org;
		try {
			org = datastore.get(orgK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.ORG_NOT_FOUND);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}

		boolean orgprivate = (boolean) org.getProperty(DSUtils.ORG_PRIVATE);
		boolean reportprivate = (boolean) report.getProperty(DSUtils.REPORT_PRIVATE);

		if(!orgprivate && reportprivate) {
			LOG.info(Log.REPORT_IS_PRIVATE);
			return Response.status(Status.FORBIDDEN).build();
		}

		Date date = new Date();

		Entity application = new Entity(DSUtils.APPLICATION, report.getKey().getName(), orgK);
		application.setProperty(DSUtils.APPLICATION_BUDGET, data.budget);
		application.setProperty(DSUtils.APPLICATION_TIME, date);

		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone(Report.PORTUGAL));

		application.setProperty(DSUtils.APPLICATION_FORMATTEDTIME, sdf.format(date));
		application.setProperty(DSUtils.APPLICATION_INFO, data.info);
		application.setProperty(DSUtils.APPLICATION_REPORT, reportK);
		
		Entity applicationLog = new Entity(DSUtils.APPLICATIONLOG, report.getKey().getName(), orgK);
		applicationLog.setProperty(DSUtils.APPLICATIONLOG_BUDGET, data.budget);
		applicationLog.setProperty(DSUtils.APPLICATIONLOG_TIME, date);
		applicationLog.setProperty(DSUtils.APPLICATIONLOG_INFO, data.info);
		applicationLog.setProperty(DSUtils.APPLICATIONLOG_ORG, orgK);
		
		Transaction txn = datastore.beginTransaction();
		
		datastore.put(txn, applicationLog);
		datastore.put(txn, application);
		txn.commit();
		
		/*
		Key reporterK = (Key) report.getProperty(DSUtils.REPORT_USER);
		Entity reporter;
		try {
			reporter = datastore.get(reporterK);
			
			String token = reporter.getProperty(DSUtils.USER_FBTOKEN).toString();
			
			Map<String, String> body = new HashMap<>();
			body.put(Prop.NIF, orgnif);
			body.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME).toString());
			body.put(Prop.ADDRESS, org.getProperty(DSUtils.ORG_ADDRESS).toString());
			body.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE).toString());
			body.put(Prop.EMAIL, org.getProperty(DSUtils.ORG_EMAIL).toString());
			
			Firebase.sendMessageToDevice(Firebase.ORG_APPLIED_TITLE, body, token);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
		} catch (FirebaseMessagingException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
		}
		*/
		
		return Response.ok().build();
	}

	@GET
	@Path("/reports")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response reports(@Context HttpServletRequest request,
			@QueryParam(ParamName.CURSOR) String cursor) {
		String nif = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				return reportsRetry(nif, cursor);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response reportsRetry(String nif, String cursor) {
		Key userK = KeyFactory.createKey(DSUtils.USER, nif);
		Key orgK = KeyFactory.createKey(userK, DSUtils.ORG, nif);

		Entity org;
		try {
			org = datastore.get(orgK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.NOT_FOUND).build();
		}

		boolean orgprivate = (boolean) org.getProperty(DSUtils.ORG_PRIVATE);

		JSONArray orgcats = new JSONArray(org.getProperty(DSUtils.ORG_CATEGORIES).toString());
		List<String> cats = new ArrayList<String>();
		for(int i = 0; i < orgcats.length(); i++)
			cats.add(orgcats.getString(i));
		
		LOG.info(orgcats.toString());

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE_BIG);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query reportQ = new Query(DSUtils.REPORT);

		JSONArray array = new JSONArray();

		reportQ.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USER, Key.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_POINTS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_THUMBNAILPATH, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_DESCRIPTION, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CATEGORY, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class));

		if(!orgprivate) {
			Filter privateF = new Query.FilterPredicate(DSUtils.REPORT_PRIVATE,
					FilterOperator.EQUAL, false);
			reportQ.setFilter(privateF);
		} else {
			reportQ.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));
		}

		QueryResultList<Entity> reportList = datastore.prepare(reportQ).asQueryResultList(fetchOptions);

		if(reportList.isEmpty())
			return Response.status(Status.NO_CONTENT).build();

		for(Entity report : reportList) {
			LOG.info(report.toString());
			String cat = report.getProperty(DSUtils.REPORT_CATEGORY).toString();
			if(cats.contains(cat))
				addReportToArray(report, array, orgK);
		}

		while(array.length() < 10) {

			fetchOptions = FetchOptions.Builder
					.withLimit(BATCH_SIZE_BIG).startCursor(reportList.getCursor());

			reportList = datastore.prepare(reportQ).asQueryResultList(fetchOptions);

			if(reportList.isEmpty() && array.length() > 10) {
				cursor = reportList.getCursor().toWebSafeString();
				return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
			} else if(reportList.isEmpty() && array.length() < 10)
				return Response.ok(array.toString()).build();

			for(Entity report : reportList) {
				String cat = report.getProperty(DSUtils.REPORT_CATEGORY).toString();
				if(cats.contains(cat))
					addReportToArray(report, array, orgK);
			}
		}

		cursor = reportList.getCursor().toWebSafeString();

		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}

	private void addReportToArray(Entity report, JSONArray array, Key orgK) {
		JSONObject jsonReport = new JSONObject();

		jsonReport.put(Prop.REPORT, report.getKey().getName());
		jsonReport.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
		jsonReport.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
		jsonReport.put(Prop.USERNAME,
				((Key) report.getProperty(DSUtils.REPORT_USER)).getName());

		Object points = report.getProperty(DSUtils.REPORT_POINTS);
		if(points != null) {
			jsonReport.put(Prop.POINTS, new JSONArray(points.toString()));
		}

		jsonReport.put(Prop.CATEGORY, report.getProperty(DSUtils.REPORT_CATEGORY));
		jsonReport.put(Prop.LAT, report.getProperty(DSUtils.REPORT_LAT));
		jsonReport.put(Prop.LNG, report.getProperty(DSUtils.REPORT_LNG));
		jsonReport.put(Prop.GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
		jsonReport.put(Prop.STATUS, report.getProperty(DSUtils.REPORT_STATUS));
		jsonReport.put(Prop.DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION));
		jsonReport.put(Prop.CREATIONTIME,
				report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
		jsonReport.put(Prop.ISPRIVATE, report.getProperty(DSUtils.REPORT_PRIVATE));

		Report.appendVotesAndComments(jsonReport, report);

		String tn = Storage.getImage(report.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());

		jsonReport.put(Prop.THUMBNAIL, tn);

		Key reportK = report.getKey();
		Key applicationK = KeyFactory.createKey(orgK, DSUtils.APPLICATION, orgK.getName());
		Key taskK = KeyFactory.createKey(reportK, DSUtils.ORGTASK, reportK.getName());

		Entity application;
		try {
			application = datastore.get(applicationK);

			jsonReport.put(Prop.BUDGET, application.getProperty(DSUtils.APPLICATION_BUDGET));
			jsonReport.put(Prop.INFO, application.getProperty(DSUtils.APPLICATION_INFO));
			jsonReport.put(Prop.APPLICATION_TIME, application.getProperty(DSUtils.APPLICATION_TIME));
		} catch(EntityNotFoundException e) {
			Entity task;
			try {
				task = datastore.get(taskK);

				if(task.getProperty(DSUtils.ORGTASK_ORG).equals(orgK)) {
					jsonReport.put(Prop.TASK_TIME, task.getProperty(DSUtils.ORGTASK_TIME));

					Query workerTQ = new Query(DSUtils.TASK).setAncestor(taskK);
					List<Entity> workers = datastore.prepare(workerTQ)
							.asList(FetchOptions.Builder.withDefaults());

					if(!workers.isEmpty()) {
						JSONArray arrayW = new JSONArray();

						for(Entity workerT : workers)
							arrayW.put(((Key) workerT.getProperty(DSUtils.TASK_WORKER)).getParent().getName());
					}
				}
			} catch(EntityNotFoundException e1) {}
		}

		array.put(jsonReport);
	}
}
