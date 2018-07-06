package com.wokesolutions.ignes.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
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
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.exceptions.NotSameNorAdminException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Prop;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/worker")
public class Worker {

	private static final Logger LOG = Logger.getLogger(Worker.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final int BATCH_SIZE = 10;

	public Worker() {}

	@POST
	@Path("/wipreport/{report}")
	public Response wipReport(@PathParam(ParamName.REPORT) String report,
			@Context HttpServletRequest request) {
		int retries = 5;

		String worker = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				return wipReportRetry(report, worker);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response wipReportRetry(String report, String worker) {
		Entity reportE ;

		try {
			reportE = datastore.get(KeyFactory.createKey(DSUtils.REPORT, report));
		} catch(EntityNotFoundException e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		Key userKey = KeyFactory.createKey(DSUtils.USER, worker);
		Query query = new Query(DSUtils.WORKER).setAncestor(userKey);

		Entity workerE;
		try {
			workerE = datastore.prepare(query).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		try {
			Entity reportStatusLog = new Entity(DSUtils.REPORTSTATUSLOG, reportE.getKey().getName(), reportE.getKey());
			reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_NEWSTATUS, Report.WIP);
			reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_OLDSTATUS,
					reportE.getProperty(DSUtils.REPORT_STATUS));
			reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_TIME, new Date());
			reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_USER, workerE.getParent());

			reportE.setProperty(DSUtils.REPORT_STATUS, Report.WIP);

			List<Entity> list = Arrays.asList(reportE, reportStatusLog);

			datastore.put(txn, list);
			LOG.info(Message.REPORT_WIPED);
			txn.commit();
			return Response.ok().build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@GET
	@Path("/tasks/{email}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response taskList(@QueryParam(ParamName.CURSOR) String cursor, @PathParam(ParamName.EMAIL) String email,
			@Context HttpServletRequest request) {

		try {
			sameUserOrAdmin(request, email);
		} catch (Exception e1) {
			LOG.info(Message.REQUESTER_IS_NOT_USER_OR_ADMIN);
			return Response.status(Status.FORBIDDEN).build();
		}

		int retries = 5;
		while(true) {
			try {
				return taskListRetry(email, cursor);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response taskListRetry(String email, String cursor) {
		LOG.info(Message.LISTING_TASKS);

		Key userK = KeyFactory.createKey(DSUtils.USER, email);
		Key workerK = KeyFactory.createKey(userK, DSUtils.WORKER, userK.getName());
		try {
			datastore.get(workerK);
		} catch(EntityNotFoundException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.NOT_FOUND).build();
		}

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);
		Query query = new Query(DSUtils.TASK).setKeysOnly();
		Filter filter = new Query.FilterPredicate(DSUtils.TASK_WORKER,
				FilterOperator.EQUAL, workerK);
		query.setFilter(filter);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		JSONArray array = new JSONArray();

		QueryResultList<Entity> tasks = datastore.prepare(query).asQueryResultList(fetchOptions);
		List<Key> keys = new ArrayList<Key>(tasks.size());
		Map<Key, Entity> taskMap = new HashMap<Key, Entity>(tasks.size());

		for(Entity task : tasks) {
			taskMap.put(task.getKey(), task);
			keys.add(task.getParent());
		}

		Map<Key, Entity> reports = datastore.get(keys);

		for(Entity task : taskMap.values()) {
			JSONObject jsonReport = new JSONObject();

			Entity report = reports.get(task.getParent());

			jsonReport.put(Prop.TASK, report.getKey().getName());
			jsonReport.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
			jsonReport.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
			jsonReport.put(Prop.USERNAME,
					((Key) report.getProperty(DSUtils.REPORT_USER)).getName());
			jsonReport.put(Prop.LAT, report.getProperty(DSUtils.REPORT_LAT));
			jsonReport.put(Prop.LNG, report.getProperty(DSUtils.REPORT_LNG));
			jsonReport.put(Prop.GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
			jsonReport.put(Prop.STATUS, report.getProperty(DSUtils.REPORT_STATUS));
			jsonReport.put(Prop.DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION));
			jsonReport.put(Prop.CREATIONTIME,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
			jsonReport.put(Prop.CREATIONTIME, report.getProperty(DSUtils.REPORT_PRIVATE));

			jsonReport.put(Prop.INDICATIONS, task.getProperty(DSUtils.TASK_INDICATIONS));
			jsonReport.put(Prop.TASK_TIME, task.getProperty(DSUtils.TASK_TIMEFORMATTED));

			Entity userKey;

			try {
				userKey = datastore.get(((Key) report.getProperty(DSUtils.REPORT_USER)));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			Query queryoptional = new Query(DSUtils.USEROPTIONAL).setAncestor(userKey.getKey());
			Entity optional;

			try {
				optional = datastore.prepare(queryoptional).asSingleEntity();
			} catch(TooManyResultsException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(optional.hasProperty(DSUtils.USEROPTIONAL_PHONE))
				jsonReport.put(Prop.PHONE,
						optional.getProperty(DSUtils.USEROPTIONAL_PHONE));

			array.put(jsonReport);
		}

		cursor = tasks.getCursor().toWebSafeString();

		if(tasks.size() < BATCH_SIZE)
			return Response.ok()
					.entity(array.toString()).build();

		return Response.ok(array.toString())
				.header(CustomHeader.CURSOR, cursor).build();
	}

	public static String sameUserOrAdmin(HttpServletRequest request, String username)
			throws NotSameNorAdminException, EntityNotFoundException {

		String requester = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Key requesterK = KeyFactory.createKey(DSUtils.USER, requester);

		Entity requesterE;
		try {
			requesterE = datastore.get(requesterK);
		} catch(EntityNotFoundException e) {
			LOG.info(Message.UNEXPECTED_ERROR);
			throw e;
		}

		String level = requesterE.getProperty(DSUtils.USER_LEVEL).toString();

		if(level.equals(UserLevel.ORG)) {
			Key orgK = KeyFactory.createKey(requesterK,  DSUtils.ORG, requester);

			Key userK = KeyFactory.createKey(DSUtils.USER, username);
			Key workerK = KeyFactory.createKey(userK,  DSUtils.WORKER, username);
			
			Entity worker = datastore.get(workerK);
			if(!((Key) worker.getProperty(DSUtils.WORKER_ORG)).equals(orgK))
				throw new NotSameNorAdminException();
			
		} else if(!level.equals(UserLevel.ADMIN) && !requester.equals(username))
			throw new NotSameNorAdminException();

		return requester;
	}
}
