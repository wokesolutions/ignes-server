package com.wokesolutions.ignes.api;

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

		String org = request.getAttribute(CustomHeader.NIF_ATT).toString();

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

			List<Entity> list = Arrays.asList(user, worker);

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

		String org = request.getAttribute(CustomHeader.NIF_ATT).toString();

		if(org == null)
			return Response.status(Status.EXPECTATION_FAILED).build();

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
		Key workerKey = KeyFactory.createKey(DSUtils.WORKER, email);
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);

		try {
			Entity worker = datastore.get(workerKey);
			Entity user = datastore.get(worker.getParent());

			if(!worker.getProperty(DSUtils.WORKER_ORG).toString().equals(org)) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}

			Entity deletedWorker = new Entity(DSUtils.DELETEDWORKER, email);
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_CREATIONTIME,
					worker.getProperty(DSUtils.WORKER_CREATIONTIME));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_JOB,
					worker.getProperty(DSUtils.WORKER_JOB));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_ORG,
					worker.getProperty(org));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_PASSWORD,
					user.getProperty(DSUtils.USER_PASSWORD));

			deletedWorker.setProperty(DSUtils.DELETEDWORKER_DELETIONTIME, new Date());

			List<Key> list = Arrays.asList(workerKey, user.getKey());

			datastore.delete(txn, list);
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
		String org = request.getAttribute(CustomHeader.NIF_ATT).toString();

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
			obj.put(DSUtils.WORKER, worker.getParent().getName());
			obj.put(DSUtils.WORKER_NAME, worker.getProperty(DSUtils.WORKER_NAME));
			obj.put(DSUtils.WORKER_JOB, worker.getProperty(DSUtils.WORKER_JOB));
			array.put(obj);
		}

		return Response.ok(array.toString()).build();
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
	public Response giveTask(TaskData data) {
		if(!data.isValid())
			return Response.status(Status.BAD_REQUEST).build();

		int retries = 5;
		while(true) {
			try {
				return giveTaskRetry(data);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response giveTaskRetry(TaskData data) {
		String report = data.report;
		String email = data.email;
		String indications = data.indications;

		Entity reportE;

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

		if(indications != null && !indications.equals(""))
			task.setProperty(DSUtils.TASK_INDICATIONS, indications);

		datastore.put(task);

		return Response.ok().build();
	}
}
