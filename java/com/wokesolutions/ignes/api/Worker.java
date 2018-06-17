package com.wokesolutions.ignes.api;

import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;

@Path("/worker")
public class Worker {

	private static final Logger LOG = Logger.getLogger(Worker.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final int BATCH_SIZE = 10;

	public Worker() {}
	
	@POST
	@Path("/wipreport/{report}")
	public Response wipReport(@PathParam(ParamName.REPORT) String report) {
		return null;
	}
	
	@GET
	@Path("/tasks/{email}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response taskList(@PathParam(ParamName.EMAIL) String email,
			@QueryParam(ParamName.CURSOR) String cursor) {
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
		
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);
		Query query = new Query(DSUtils.TASK);
		Filter filter = new Query.FilterPredicate(DSUtils.TASK_WORKER, FilterOperator.EQUAL, email);
		query.setFilter(filter);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		
		JSONArray array = new JSONArray();
		
		QueryResultList<Entity> reports = datastore.prepare(query).asQueryResultList(fetchOptions);
		
		Report.buildJsonReports(reports, true);
		
		return Response.ok(array.toString()).build();
	}
}
