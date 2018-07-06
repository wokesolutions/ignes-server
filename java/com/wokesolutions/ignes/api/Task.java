package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Prop;

@Path("/task")
public class Task {

	private static final Logger LOG = Logger.getLogger(Task.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final int BATCH_SIZE = 10;
	
	public Task() {}

	@POST
	@Path("/addnote/{task}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response addNote(String note, @PathParam(ParamName.TASK) String task,
			@Context HttpServletRequest request) {
		if(note == null || note.equals("")) {
			LOG.info(Message.NOTE_EMPTY);
			return Response.status(Status.BAD_REQUEST).build();
		} else if(note.length() > 400) {
			LOG.info(Message.NOTE_TOO_LONG);
			return Response.status(Status.BAD_REQUEST).build();
		}

		String worker = request.getAttribute(CustomHeader.USERNAME_ATT).toString();
		
		int retries = 5;
		while(true) {
			try {
				return addNoteRetry(note, task, worker);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response addNoteRetry(String note, String task, String worker) {
		Date time = new Date();
		
		Entity noteE = new Entity(DSUtils.NOTE, makeId(task, worker, time));

		Query query = new Query(DSUtils.TASK).setAncestor(KeyFactory.createKey(DSUtils.REPORT, task));

		try {
			datastore.prepare(query).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.TASK_NOT_FOUND);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

		JSONObject obj = new JSONObject(note);
		note = obj.getString(Prop.NOTE);
		
		noteE.setProperty(DSUtils.NOTE_TASK, task);
		noteE.setUnindexedProperty(DSUtils.NOTE_TEXT, note);
		noteE.setProperty(DSUtils.NOTE_WORKER, worker);
		noteE.setProperty(DSUtils.NOTE_TIME, time);

		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone(Report.PORTUGAL));
		
		noteE.setProperty(DSUtils.NOTE_TIMEFORMATTED, sdf.format(time));
		
		datastore.put(noteE);

		return Response.ok().build();
	}
	
	@GET
	@Path("/notes/{task}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getNotes(@PathParam(ParamName.TASK) String taskid,
			@QueryParam(ParamName.CURSOR) String cursor) {
		
		if(taskid == null || taskid.equals(""))
			return Response.status(Status.BAD_REQUEST).build();
		
		int retries = 5;
		while(true) {
			try {
				return getNotesRetry(taskid, cursor);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}
	
	private Response getNotesRetry(String taskid, String cursor) {
		List<Entity> task = null;
		Query query = new Query(DSUtils.TASK).setAncestor(KeyFactory.createKey(DSUtils.REPORT, taskid));
		
		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);
		
		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		
		try {
			task = datastore.prepare(query).asQueryResultList(FetchOptions.Builder.withLimit(1));
		} catch(TooManyResultsException e) {}
		
		if(task.isEmpty()) {
			LOG.info(Message.TASK_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}
		
		Query query2 = new Query(DSUtils.NOTE);
		Filter filter = new Query.FilterPredicate(DSUtils.NOTE_TASK, FilterOperator.EQUAL, taskid);
		query2.setFilter(filter);
		
		QueryResultList<Entity> notes = datastore.prepare(query2).asQueryResultList(fetchOptions);
		
		JSONArray array = new JSONArray();
		
		for(Entity note : notes) {
			JSONObject obj = new JSONObject();
			
			obj.put(Prop.NOTE, note.getKey().getName());
			obj.put(Prop.TASK, note.getProperty(DSUtils.NOTE_TASK));
			obj.put(Prop.CREATIONTIME, note.getProperty(DSUtils.NOTE_TIMEFORMATTED));
			obj.put(Prop.TEXT, note.getProperty(DSUtils.NOTE_TEXT));
			obj.put(Prop.WORKER, note.getProperty(DSUtils.NOTE_WORKER));
			
			array.put(obj);
		}
		
		if(notes.isEmpty())
			return Response.status(Status.NO_CONTENT).build();

		if(notes.size() < BATCH_SIZE)
			return Response.ok()
					.entity(array.toString()).build();
		
		cursor = notes.getCursor().toWebSafeString();
		return Response.ok(array.toString()).header(CustomHeader.CURSOR, cursor).build();
	}
	
	private static String makeId(String task, String worker, Date time) {
		String tim = Long.toString(time.getTime());
		tim = tim.substring(5, tim.length());
		
		String id = task.charAt(0) + worker.substring(0, 2) + tim;
		
		return id;
	}
}
