package com.wokesolutions.ignes.api;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONObject;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.ReportData;
import com.wokesolutions.ignes.util.Boundaries;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Haversine;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.PropertyValue;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Storage;

@Path("/report")
public class Report {

	private static final Logger LOG = Logger.getLogger(Report.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static final int BATCH = 10;

	public Report() {}

	@POST
	@Path("/create")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createReport(ReportData data,
			@Context HttpServletRequest request) {
		if(!data.isValid())
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return createReportRetry(data, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Message.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}
				retries--;
			}
		}
	}

	private Response createReportRetry(ReportData data, HttpServletRequest request) {
		String username = null;
		Key reportKey = null;
		long creationtime = System.currentTimeMillis();
		String reportid = null;
		Transaction txn = datastore.beginTransaction();

		try {
			username = request.getAttribute(CustomHeader.USERNAME).toString();
			reportid = ReportData.generateId(username, creationtime);
			LOG.info(Message.ATTEMPT_CREATE_REPORT + reportid);
			reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
			datastore.get(reportKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.DUPLICATE_REPORT).build();
		} catch (EntityNotFoundException e) {
			if(username == null || reportKey == null)
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			Entity report = new Entity(DSUtils.REPORT, reportid);

			report.setProperty(DSUtils.REPORT_ADDRESS, data.report_address);
			report.setProperty(DSUtils.REPORT_LAT, data.report_lat);
			report.setProperty(DSUtils.REPORT_LNG, data.report_lng);
			report.setProperty(DSUtils.REPORT_CREATIONTIME, new Date(creationtime));
			report.setUnindexedProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED,
					new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(creationtime));
			report.setProperty(DSUtils.REPORT_STATUS, PropertyValue.OPEN);
			report.setProperty(DSUtils.REPORT_USERNAME, username);
			report.setUnindexedProperty(DSUtils.REPORT_CREATIONLATLNG, request.getHeader(CustomHeader.APPENGINE_LATLNG));
			if(data.report_title != null)
				report.setUnindexedProperty(DSUtils.REPORT_TITLE, data.report_title);
			if(data.report_locality != null)
				report.setProperty(DSUtils.REPORT_LOCALITY, data.report_locality);
			if(data.report_city != null)
				report.setProperty(DSUtils.REPORT_DISTRICT, data.report_city);
			if(data.report_gravity >= 1 && data.report_gravity <= 5)
				report.setProperty(DSUtils.REPORT_GRAVITY, data.report_gravity);
			if(data.report_description != null)
				report.setUnindexedProperty(DSUtils.REPORT_DESCRIPTION, data.report_description);

			String imgid = Storage.IMG_FOLDER + Storage.REPORT_FOLDER + reportid;
			if(!Storage.saveImage(data.report_img, Storage.BUCKET, imgid))
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Message.STORAGE_ERROR).build();

			report.setUnindexedProperty(DSUtils.REPORT_IMGPATH, imgid);

			String thumbnailid = Storage.IMG_FOLDER + Storage.REPORT_FOLDER + Storage.THUMBNAIL_FOLDER + reportid;
			if(!Storage.saveImage(data.report_thumbnail, Storage.BUCKET, thumbnailid))
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Message.STORAGE_ERROR).build();

			report.setUnindexedProperty(DSUtils.REPORT_THUMBNAILPATH, thumbnailid);

			Entity reportVotes = new Entity(DSUtils.REPORT_VOTES, reportKey);
			reportVotes.setProperty(DSUtils.REPORT_VOTES_UP, 0L);
			reportVotes.setProperty(DSUtils.REPORT_VOTES_DOWN, 0L);

			Entity reportComments = new Entity(DSUtils.REPORT_COMMENTS, reportKey);
			reportComments.setProperty(DSUtils.REPORTCOMMENTS_NUM, 0);

			List<Entity> entities = Arrays.asList(report, reportVotes, reportComments);

			LOG.info(Message.REPORT_CREATED + reportid);
			datastore.put(txn, entities);
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
	@Path("/getwithinboundaries")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsWithinBoundaries(
			@QueryParam(ParamName.MINLAT) double minlat,
			@QueryParam(ParamName.MINLNG) double minlng,
			@QueryParam(ParamName.MAXLAT) double maxlat,
			@QueryParam(ParamName.MAXLNG) double maxlng,
			@QueryParam(ParamName.CURSOR) String cursor,
			@Context HttpServletRequest request) {
		if(minlat == 0 || minlng == 0 || maxlat == 0 || maxlng == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinBoundariesRetry(minlat, minlng, maxlat, maxlng, cursor, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinBoundariesRetry(double minlat, double minlng,
			double maxlat, double maxlng, String cursor,
			HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_GIVE_ALL_REPORTS);

		Filter minlatFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LAT, FilterOperator.GREATER_THAN, minlat);
		Filter maxlatFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LAT, FilterOperator.LESS_THAN, maxlat);
		Filter openFilter = 
				new Query.FilterPredicate(DSUtils.REPORT_STATUS, FilterOperator.EQUAL, PropertyValue.OPEN);
		Filter showFilter = 
				new Query.FilterPredicate(DSUtils.REPORT_STATUS, FilterOperator.EQUAL, PropertyValue.SHOW);

		Filter positionFilters = CompositeFilterOperator.and(minlatFilter, maxlatFilter);
		Filter visibilityFilters = CompositeFilterOperator.or(openFilter, showFilter);
		Filter allFilters = CompositeFilterOperator.and(positionFilters, visibilityFilters);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH);

		if(cursor != null)
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		QueryResultList<Entity> reports =
				datastore.prepare(latlngQuery).asQueryResultList(fetchOptions);

		boolean append = request.getAttribute(CustomHeader.LEVEL) != null;

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			String reportsJson = null;
			try {
				reportsJson = reportJsonList(reports, append);
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			return Response.ok()
					.entity(reportsJson).build();
		}
	}

	@GET
	@Path("/getwithinradius")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsWithinRadius(
			@QueryParam(ParamName.LAT) double lat,
			@QueryParam(ParamName.LNG) double lng,
			@QueryParam(ParamName.RADIUS) double radius,
			@QueryParam(ParamName.CURSOR) String cursor,
			@Context HttpServletRequest request) {
		if(lat == 0 || lng == 0 || radius == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinRadiusRetry(lat, lng, radius, cursor, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinRadiusRetry(double lat, double lng,
			double radius, String cursor,
			HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_GIVE_ALL_REPORTS);

		Boundaries bound = Haversine.getBoundaries(lat, lng, radius);

		Filter minlatFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LAT, FilterOperator.GREATER_THAN, bound.minlat);
		Filter maxlatFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LAT, FilterOperator.LESS_THAN, bound.maxlat);
		Filter openFilter = 
				new Query.FilterPredicate(DSUtils.REPORT_STATUS, FilterOperator.EQUAL, PropertyValue.OPEN);
		Filter showFilter = 
				new Query.FilterPredicate(DSUtils.REPORT_STATUS, FilterOperator.EQUAL, PropertyValue.SHOW);

		Filter positionFilters = CompositeFilterOperator.and(minlatFilter, maxlatFilter);
		Filter visibilityFilters = CompositeFilterOperator.or(openFilter, showFilter);
		Filter allFilters = CompositeFilterOperator.and(positionFilters, visibilityFilters);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH);

		if(cursor != null)
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		QueryResultList<Entity> reports =
				datastore.prepare(latlngQuery).asQueryResultList(fetchOptions);

		boolean append = request.getAttribute(CustomHeader.LEVEL) != null;

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			String reportsJson = null;
			try {
				reportsJson = reportJsonList(reports, append);
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			return Response.ok()
					.entity(reportsJson).build();
		}
	}

	@GET
	@Path("/getinlocation")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsInLocation(@QueryParam(ParamName.LOCATION) String district,
			@QueryParam(ParamName.CURSOR) String cursor,
			@Context HttpServletRequest request) {
		if(district == null)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsInLocationRetry(district, cursor, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsInLocationRetry(String location,
			String cursor, HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_GIVE_ALL_REPORTS);

		Filter cityFilter =
				new Query.FilterPredicate(DSUtils.REPORT_DISTRICT, FilterOperator.EQUAL, location);
		Filter localityFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LOCALITY, FilterOperator.EQUAL, location);
		Filter openFilter =
				new Query.FilterPredicate(DSUtils.REPORT_STATUS, FilterOperator.EQUAL, PropertyValue.OPEN);
		Filter showFilter =
				new Query.FilterPredicate(DSUtils.REPORT_STATUS, FilterOperator.EQUAL, PropertyValue.SHOW);

		Filter visibilityFilters = CompositeFilterOperator.or(openFilter, showFilter);
		Filter locationFilter = CompositeFilterOperator.or(cityFilter, localityFilter);
		Filter allFilters = CompositeFilterOperator.and(locationFilter, visibilityFilters);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH);

		if(cursor != null)
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		QueryResultList<Entity> reports =
				datastore.prepare(latlngQuery).asQueryResultList(fetchOptions);

		boolean append = request.getAttribute(CustomHeader.LEVEL) != null;

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			String reportsJson = null;
			try {
				reportsJson = reportJsonList(reports, append);
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			return Response.ok()
					.entity(reportsJson).build();
		}
	}

	@POST
	@Path("/vote/up/{report}")
	public Response upvoteReport(@PathParam (ParamName.REPORT) String report) {
		if(report == null || report == "")
			return Response.status(Status.BAD_REQUEST).build();
		int retries = 5;
		while(true) {
			try {
				return upvoteReportRetry(report);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response upvoteReportRetry(String reportid) {
		Key reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Query votesQuery = new Query(DSUtils.REPORT_VOTES).setAncestor(reportKey);
		List<Entity> results = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());
		if(results.isEmpty()) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		Entity votes = results.get(0);
		votes.setProperty(DSUtils.REPORT_VOTES_UP, (long) votes.getProperty(DSUtils.REPORT_VOTES_UP) + 1L); 

		datastore.put(votes);
		LOG.info(Message.VOTED_REPORT);
		return Response.ok().build();
	}

	@POST
	@Path("/vote/down/{report}")
	public Response downvoteReport(@PathParam (ParamName.REPORT) String report) {
		if(report == null || report == "")
			return Response.status(Status.BAD_REQUEST).build();
		int retries = 5;
		while(true) {
			try {
				return downvoteReportRetry(report);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response downvoteReportRetry(String reportid) {
		Key reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Query votesQuery = new Query(DSUtils.REPORT_VOTES).setAncestor(reportKey);
		List<Entity> results = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());
		if(results.isEmpty()) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		Entity votes = results.get(0);
		votes.setProperty(DSUtils.REPORT_VOTES_DOWN, (long) votes.getProperty(DSUtils.REPORT_VOTES_DOWN) + 1L); 

		datastore.put(votes);
		LOG.info(Message.VOTED_REPORT);
		return Response.ok().build();
	}

	@GET
	@Path("/vote/{report}")
	public Response getVotes(@PathParam (ParamName.REPORT) String report) {
		if(report == null || report == "")
			return Response.status(Status.BAD_REQUEST).build();
		int retries = 5;
		while(true) {
			try {
				return getVotesRetry(report);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	public Response getVotesRetry(String reportid) {
		Entity report = getReportVotesEntity(reportid);
		if(report == null) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONObject object = new JSONObject();
		object.put(DSUtils.REPORT, report.getKey().getParent().getName());
		object.put(DSUtils.REPORT_VOTES_UP, report.getProperty(DSUtils.REPORT_VOTES_UP));
		object.put(DSUtils.REPORT_VOTES_DOWN, report.getProperty(DSUtils.REPORT_VOTES_DOWN));

		LOG.info(Message.VOTED_REPORT);
		return Response.ok(object).build();
	}

	private void appendVotes(JSONObject reportJson, Entity report) 
			throws DatastoreException {
		Entity votes = getReportVotesEntity(report.getKey().getName());
		reportJson.put(DSUtils.REPORT_VOTES_UP, votes.getProperty(DSUtils.REPORT_VOTES_UP).toString());
		reportJson.put(DSUtils.REPORT_VOTES_DOWN, votes.getProperty(DSUtils.REPORT_VOTES_DOWN).toString());
	}

	private Entity getReportVotesEntity(String reportid) 
			throws DatastoreException {
		Key reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Query votesQuery = new Query(DSUtils.REPORT_VOTES).setAncestor(reportKey);
		List<Entity> results = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());
		if(results.isEmpty()) {
			LOG.info(Message.REPORT_NOT_FOUND);
			throw new DatastoreException(new IOException());
		}

		return results.get(0);
	}

	private String reportJsonList(QueryResultList<Entity> list, boolean append) 
			throws DatastoreException {
		String reportList = "[{" + ParamName.CURSOR + ": " + list.getCursor() + "}, ";

		for(Entity report : list) {
			JSONObject reportJson = new JSONObject();

			Filter numFilter =
					new Query.FilterPredicate(DSUtils.REPORTCOMMENTS_NUM,
							FilterOperator.GREATER_THAN_OR_EQUAL, 0);

			Query commentQuery = new Query(DSUtils.REPORT_COMMENTS)
					.setFilter(numFilter).setAncestor(report.getKey());

			List<Entity> comment = datastore.prepare(commentQuery).asList(FetchOptions.Builder.withDefaults());

			int numComments = 0;

			if(!comment.isEmpty()) {
				numComments = (int) comment.get(0).getProperty(DSUtils.REPORTCOMMENTS_NUM);
				// throw new DatastoreException(new IOException());
			}

			reportJson.put(DSUtils.REPORT, report.getKey().getName());
			reportJson.put(DSUtils.REPORT_LAT, report.getProperty(DSUtils.REPORT_LAT).toString());
			LOG.info(reportJson.getString(DSUtils.REPORT_LAT));
			reportJson.put(DSUtils.REPORT_LNG, report.getProperty(DSUtils.REPORT_LNG).toString());
			reportJson.put(DSUtils.REPORT_STATUS, report.getProperty(DSUtils.REPORT_STATUS).toString());
			reportJson.put(DSUtils.REPORT_ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS).toString());
			reportJson.put(DSUtils.REPORT_CREATIONTIMEFORMATTED,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED).toString());
			reportJson.put(DSUtils.REPORT_USERNAME, report.getProperty(DSUtils.REPORT_USERNAME).toString());
			if(report.hasProperty(DSUtils.REPORT_GRAVITY))
				reportJson.put(DSUtils.REPORT_GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY).toString());
			if(report.hasProperty(DSUtils.REPORT_DESCRIPTION))
				reportJson.put(DSUtils.REPORT_DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION).toString());
			if(report.hasProperty(DSUtils.REPORT_TITLE))
				reportJson.put(DSUtils.REPORT_TITLE, report.getProperty(DSUtils.REPORT_TITLE).toString());
			reportJson.put(DSUtils.REPORT_COMMENTSNUM, numComments);

			reportJson.put(DSUtils.REPORT_THUMBNAIL, Storage.getImage((String)
					report.getProperty(DSUtils.REPORT_THUMBNAILPATH)));

			if(append)
				appendVotes(reportJson, report);

			String reportString = reportJson.toString();

			reportList += reportString + ", ";
		}

		reportList = reportList.substring(0, reportList.length() - 2) + "]";
		return reportList;
	}
}
