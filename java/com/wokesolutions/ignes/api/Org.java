package com.wokesolutions.ignes.api;

import java.util.Date;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.WorkerRegisterData;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.Message;

@Path("/org")
public class Org {

	private static final Logger LOG = Logger.getLogger(Org.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	@POST
	@Path("/registerworker")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response registerWorker(WorkerRegisterData registerData,
			@Context HttpServletRequest request) {
		if(!registerData.isValid())
			return Response.status(Status.BAD_REQUEST).entity(Message.REGISTER_DATA_INVALID).build();

		String org = request.getAttribute(CustomHeader.NIF).toString();

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

		String email = registerData.worker_email;
		Key workerKey = KeyFactory.createKey(DSUtils.WORKER, email);

		Filter emailFilter =
				new Query.FilterPredicate(DSUtils.USER_EMAIL, FilterOperator.EQUAL, email);
		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		Query userQuery = new Query(DSUtils.USER)
				.setFilter(emailFilter);

		QueryResultList<Entity> userResult =
				datastore.prepare(userQuery).asQueryResultList(fetchOptions);

		Key orgKey = KeyFactory.createKey(DSUtils.ORG, org);

		if(!userResult.isEmpty()) {
			return Response.status(Status.CONFLICT).entity(Message.USER_ALREADY_EXISTS).build();
		}

		String pw = WorkerRegisterData.generateCode(org, email);

		Entity worker = new Entity(DSUtils.WORKER, workerKey);
		worker.setUnindexedProperty(DSUtils.WORKER_PASSWORD, DigestUtils.sha256Hex(pw));
		worker.setProperty(DSUtils.WORKER_ORG, org);
		worker.setProperty(DSUtils.WORKER_JOB, registerData.worker_job);
		worker.setUnindexedProperty(DSUtils.WORKER_CREATIONTIME, new Date());

		try {
			Entity orgEntity = datastore.get(orgKey);
			Email.sendWorkerRegisterMessage(email, pw,
					orgEntity.getProperty(DSUtils.ORG_NAME).toString());
		} catch(EntityNotFoundException e) {
			return Response.status(Status.EXPECTATION_FAILED).build();
		}
		datastore.put(worker);
		LOG.info(Message.WORKER_REGISTERED + registerData.worker_email);
		return Response.ok().build();
	}
	
	@DELETE
	@Path("/deleteworker")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response deleteWorker(@QueryParam("workeremail") String workeremail,
			@Context HttpServletRequest request) {
		if(!isValid(workeremail))
			return Response.status(Status.BAD_REQUEST).build();

		String org = request.getAttribute(CustomHeader.NIF).toString();

		if(org == null)
			return Response.status(Status.EXPECTATION_FAILED).build();

		int retries = 5;
		while(true) {
			try {
				return deleteWorkerRetry(workeremail, org);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}
	
	public Response deleteWorkerRetry(String workeremail, String org) {
		Key workerKey = KeyFactory.createKey(DSUtils.WORKER, workeremail);
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);
		
		try {
			Entity worker = datastore.get(workerKey);
			
			if(!worker.getProperty(DSUtils.WORKER_ORG).toString().equals(org)) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).build();
			}
			
			Entity deletedWorker = new Entity(DSUtils.DELETEDWORKER, workeremail);
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_CREATIONTIME,
					worker.getProperty(DSUtils.WORKER_CREATIONTIME));
			
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_JOB,
					worker.getProperty(DSUtils.WORKER_JOB));
			
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_ORG,
					worker.getProperty(org));
			
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_PASSWORD,
					worker.getProperty(DSUtils.WORKER_PASSWORD));
			
			deletedWorker.setProperty(DSUtils.DELETEDWORKER_DELETIONTIME, new Date());
			
			datastore.delete(txn, workerKey);
			datastore.put(txn, deletedWorker);
			
			LOG.info(Message.DELETED_WORKER + workeremail);
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
	
	private boolean isValid(String email) {
		if(email == null)
			return false;
		if(email == "")
			return false;
		if(!email.contains("@"))
			return false;
		return true;
	}
}
