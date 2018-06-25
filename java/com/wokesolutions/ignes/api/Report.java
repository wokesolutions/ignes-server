package com.wokesolutions.ignes.api;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
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
import com.google.appengine.api.datastore.PropertyContainer;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.callbacks.LevelManager;
import com.wokesolutions.ignes.data.ReportData;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Haversine;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Storage;
import com.wokesolutions.ignes.util.Storage.StoragePath;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/report")
public class Report {

	private static final Logger LOG = Logger.getLogger(Report.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
	private static final int BATCH_SIZE = 10;

	public static final String OPEN = "open";
	public static final String CLOSED = "closed";
	public static final String STANDBY = "standby";
	private static final String NOT_FOUND = "NOT_FOUND";

	private static final int DEFAULT_GRAVITY = 2;
	private static final int NO_TRUST_GRAVITY = 1;

	public Report() {}

	@POST
	@Path("/create")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
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
		Date creationtime = new Date();
		String reportid = null;
		Transaction txn = datastore.beginTransaction();

		LOG.info(Message.ATTEMPT_CREATE_REPORT);

		String level = request.getAttribute(CustomHeader.LEVEL_ATT).toString();

		try {
			username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

			reportid = ReportData.generateId(username, creationtime);
			LOG.info(Message.ATTEMPT_CREATE_REPORT + reportid);
			reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
			datastore.get(reportKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Message.DUPLICATE_REPORT).build();
		} catch(EntityNotFoundException e1) {
			try {
				reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
				datastore.get(reportKey);
				txn.rollback();
				return Response.status(Status.CONFLICT).entity(Message.DUPLICATE_REPORT).build();
			} catch(EntityNotFoundException e2) {
				if(username == null || reportKey == null) {
					LOG.info(Message.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				Entity report = new Entity(DSUtils.REPORT, reportid);

				report.setProperty(DSUtils.REPORT_LAT, data.report_lat);
				report.setProperty(DSUtils.REPORT_LNG, data.report_lng);
				report.setProperty(DSUtils.REPORT_CREATIONTIME, creationtime);
				report.setProperty(DSUtils.REPORT_PRIVATE, data.report_private);
				report.setProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED,
						new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(creationtime));
				report.setProperty(DSUtils.REPORT_USERNAME, username);

				if(level.equals(UserLevel.LEVEL1))
					report.setProperty(DSUtils.REPORT_STATUS, STANDBY);
				else
					report.setProperty(DSUtils.REPORT_STATUS, OPEN);

				report.setUnindexedProperty(DSUtils.REPORT_CREATIONLATLNG,
						request.getHeader(CustomHeader.APPENGINE_LATLNG));

				Query userpointsQ = new Query(DSUtils.USERPOINTS)
						.setAncestor(KeyFactory.createKey(DSUtils.USER, username));

				int points;
				try {
					Entity pointsE = datastore.prepare(userpointsQ).asSingleEntity();

					points = Integer.parseInt(pointsE.getProperty(DSUtils.USERPOINTS_POINTS).toString());
				} catch(TooManyResultsException e3) {
					LOG.info(Message.UNEXPECTED_ERROR);
					txn.rollback();
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				if(data.report_gravity >= 1 && data.report_gravity <= 5) {
					report.setProperty(DSUtils.REPORT_GRAVITY, data.report_gravity);

					if(data.report_gravity == 5 && level.equals(UserLevel.LEVEL2))
						report.setProperty(DSUtils.REPORT_STATUS, STANDBY);

				} else {
					int gravity = DEFAULT_GRAVITY;
					if(points < LevelManager.LEVEL2_POINTS)
						gravity = NO_TRUST_GRAVITY;

					report.setProperty(DSUtils.REPORT_GRAVITY, gravity);
				}

				if(data.report_address != null)
					report.setProperty(DSUtils.REPORT_ADDRESS, data.report_address);

				if(data.report_title != null)
					report.setProperty(DSUtils.REPORT_TITLE, data.report_title);
				else
					report.setProperty(DSUtils.REPORT_TITLE, "");

				if(data.report_description != null)
					report.setProperty(DSUtils.REPORT_DESCRIPTION, data.report_description);
				else
					report.setProperty(DSUtils.REPORT_DESCRIPTION, "");

				if(data.report_locality != null)
					report.setProperty(DSUtils.REPORT_LOCALITY, data.report_locality);
				if(data.report_city != null)
					report.setProperty(DSUtils.REPORT_DISTRICT, data.report_city);

				LinkedList<String> folders = new LinkedList<String>();
				folders.add(Storage.IMG_FOLDER);
				folders.add(Storage.REPORT_FOLDER);
				StoragePath pathImg = new StoragePath(folders, reportid);
				if(!Storage.saveImage(data.report_img, Storage.BUCKET, pathImg,
						data.report_imgwidth, data.report_imgheight, data.report_imgorientation)) {
					LOG.info(Message.STORAGE_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Message.STORAGE_ERROR).build();
				}

				report.setUnindexedProperty(DSUtils.REPORT_IMGPATH, pathImg.makePath());
				report.setUnindexedProperty(DSUtils.REPORT_THUMBNAILPATH, pathImg.makeTnPath());

				Entity reportVotes = new Entity(DSUtils.REPORTVOTES, reportKey);
				reportVotes.setProperty(DSUtils.REPORTVOTES_UP, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_DOWN, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_DOWN, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE, 0);

				report.setPropertiesFrom(makeCoordProps(data.report_lat, data.report_lng));

				List<Entity> entities = Arrays.asList(report, reportVotes);

				LOG.info(Message.REPORT_CREATED + reportid);
				datastore.put(txn, entities);
				txn.commit();

				return Response.ok().build();
			}
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	/*
	@GET
	@Path("/getwithinboundaries")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsWithinBoundaries(
			@QueryParam(ParamName.MINLAT) double minlat,
			@QueryParam(ParamName.MINLNG) double minlng,
			@QueryParam(ParamName.MAXLAT) double maxlat,
			@QueryParam(ParamName.MAXLNG) double maxlng,
			@Context HttpServletRequest request) {
		if(minlat == 0 || minlng == 0 || maxlat == 0 || maxlng == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinBoundariesRetry(minlat, minlng, maxlat, maxlng, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinBoundariesRetry(double minlat, double minlng,
			double maxlat, double maxlng,
			HttpServletRequest request) {
		LOG.info(Message.ATTEMPT_GIVE_ALL_REPORTS);

		Filter minlatFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LAT, FilterOperator.GREATER_THAN, minlat);
		Filter maxlatFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LAT, FilterOperator.LESS_THAN, maxlat);

		Filter positionFilters = CompositeFilterOperator.and(minlatFilter, maxlatFilter);

		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		Query latlngQuery = new Query(DSUtils.OPENREPORT)
				.setFilter(positionFilters);

		QueryResultList<Entity> reports =
				datastore.prepare(latlngQuery).asQueryResultList(fetchOptions);

		JSONArray jsonReports;

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				jsonReports = buildJsonReports(reports);
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			return Response.ok()
					.entity(jsonReports.toString()).build();
		}
	}
	 */

	@GET
	@Path("/thumbnail/{report}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getThumbnail(@PathParam(ParamName.REPORT) String report) {
		int retries = 5;
		while(true) {
			try {
				return getThumbnailRetry(report);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getThumbnailRetry(String report) {
		Entity rep;

		try {
			rep = datastore.get(KeyFactory.createKey(DSUtils.REPORT, report));
		} catch(EntityNotFoundException e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONObject obj = new JSONObject();
		try {
			String tn = Storage.getImage(rep.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());
			obj.put(DSUtils.REPORT_THUMBNAIL, tn);

			LOG.info(tn);
		} catch(Exception e) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		return Response.ok(obj.toString()).build();
	}

	@GET
	@Path("/getwithinradius")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getReportsWithinRadius(
			@QueryParam(ParamName.LAT) double lat,
			@QueryParam(ParamName.LNG) double lng,
			@QueryParam(ParamName.RADIUS) double radius,
			@QueryParam(ParamName.CURSOR) String cursor) {
		if(lat == 0 || lng == 0 || radius == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinRadiusRetry(lat, lng, radius, cursor);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinRadiusRetry(double lat, double lng,
			double radius, String cursor) {
		LOG.info(Message.ATTEMPT_GIVE_ALL_REPORTS);

		int precision = Haversine.getPrecision(lat, lng, radius);

		LOG.info("precision " + Integer.toString(precision));

		String latValue = doubleToProp(lat, precision);
		String lngValue = doubleToProp(lng, precision);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		String pPropLat = "report_p" + precision + "lat";
		String pPropLng = "report_p" + precision + "lng";

		Filter latFilter =
				new Query.FilterPredicate(pPropLat,
						FilterOperator.EQUAL, latValue);

		Filter lngFilter =
				new Query.FilterPredicate(pPropLng,
						FilterOperator.EQUAL, lngValue);

		List<Filter> filters = Arrays.asList(latFilter, lngFilter);

		Filter filter = new Query.CompositeFilter(CompositeFilterOperator.AND, filters);

		Query reportQuery = new Query(DSUtils.REPORT)
				.setFilter(filter);

		LOG.info(Message.SEARCHING_IN_COORDS + pPropLat + " - " + latValue +
				" | " + pPropLng + " - " + lngValue);

		reportQuery
		.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USERNAME, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_DESCRIPTION, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));

		QueryResultList<Entity> reports =
				datastore.prepare(reportQuery).asQueryResultList(fetchOptions);

		cursor = reports.getCursor().toWebSafeString();

		JSONArray jsonReports;

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				jsonReports = buildJsonReports(reports, false);
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			if(reports.size() < BATCH_SIZE)
				return Response.ok()
						.entity(jsonReports.toString()).build();

			return Response.ok()
					.entity(jsonReports.toString()).header(CustomHeader.CURSOR, cursor).build();
		}
	}

	@GET
	@Path("/getinlocation")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getReportsInLocation(@QueryParam(ParamName.LOCATION) String location,
			@QueryParam(ParamName.CURSOR) String cursor) {
		if(location == null || location == "")
			return Response.status(Status.EXPECTATION_FAILED).build();

		String cacheKey = encodeCacheKey(true, location, cursor);

		if(cache.contains(cacheKey)) {
			LOG.info(Message.USING_CACHE);

			String cacheKeyNum = cacheKey + "#";
			if(cache.increment(cacheKeyNum, 1L) == null)
				cache.put(cacheKeyNum, 0, Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(15)));

			return Response.ok().entity(cache.get(cacheKey)).build();
		}

		int retries = 5;
		while(true) {
			try {
				return getReportsInLocationRetry(location, cursor, cacheKey);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsInLocationRetry(String location, String cursor, String cacheKey) {
		LOG.info(Message.ATTEMPT_GIVE_ALL_REPORTS);

		Filter localityFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LOCALITY,
						FilterOperator.EQUAL, location);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query reportQuery = new Query(DSUtils.REPORT).setFilter(localityFilter);

		reportQuery.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USERNAME, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_DESCRIPTION, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));

		QueryResultList<Entity> reports =
				datastore.prepare(reportQuery).asQueryResultList(fetchOptions);

		JSONArray jsonReports;

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				jsonReports = buildJsonReports(reports, false);
			} catch(InternalServerErrorException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			String cacheKeyNum = cacheKey + "#";
			if(cache.increment(cacheKeyNum, 1L) == null)
				cache.put(cacheKeyNum, 0L, Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(15)));

			if((long) cache.get(cacheKeyNum) >= 5L)
				cache.put(cacheKey, jsonReports.toString(),
						Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(15)));

			cursor = reports.getCursor().toWebSafeString();

			if(jsonReports.length() < BATCH_SIZE)
				return Response.ok(jsonReports.toString()).build();

			return Response.ok()
					.entity(jsonReports.toString()).header(CustomHeader.CURSOR, cursor).build();
		}
	}

	@GET
	@Path("/thumbnails")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getThumbnails(@Context HttpHeaders headers) {
		int retries = 5;

		String header = headers.getHeaderString(CustomHeader.REPORTS);

		if(header == null || header.equals("") || header.contains(",")) {
			LOG.info(Message.NO_REPORTS_IN_HEADER);
			return Response.status(Status.BAD_REQUEST).build();
		}

		while(true) {
			try {
				return getThumbnailsRetry(header);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getThumbnailsRetry(String header) {
		List<String> ids = decodeReportsHeader(header);
		if(ids.size() > 10) {
			LOG.info(Message.TOO_MANY_REPORTS);
			return Response.status(Status.BAD_REQUEST).build();
		}

		JSONObject thumbnails = new JSONObject();

		for(String id : ids) {
			Entity report;

			try {
				report = datastore.get(KeyFactory.createKey(DSUtils.REPORT, id));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.REPORT_NOT_FOUND + " - " + id);

				thumbnails.put(id, NOT_FOUND);

				continue;
			}

			String tnPath = report.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString();

			String tn = Storage.getImage(tnPath);

			thumbnails.put(id, tn);
		}

		return Response.ok(thumbnails.toString()).build();
	}

	private List<String> decodeReportsHeader(String header) {
		String[] ids = header.split("&");
		return Arrays.asList(ids);
	}

	@POST
	@Path("/close/{report}")
	public Response closeReport(@PathParam("report") String report) {
		int retries = 5;

		if(report == null || report.equals(""))
			return Response.status(Status.BAD_REQUEST).build();

		while(true) {
			try {
				return closeReportRetry(report);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response closeReportRetry(String report) {
		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		try {
			Entity reportEnt;
			Key key = KeyFactory.createKey(DSUtils.REPORT, report);

			try {
				reportEnt = datastore.get(key);
			} catch (EntityNotFoundException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			Entity closedRep = new Entity(DSUtils.CLOSEDREPORT, report);

			closedRep.setPropertiesFrom(reportEnt);
			closedRep.setProperty(DSUtils.REPORT_STATUS, CLOSED);

			datastore.delete(txn, key);

			datastore.put(txn, closedRep);

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

	public static JSONArray buildJsonReports(QueryResultList<Entity> reports, boolean withTn) {
		JSONArray array = new JSONArray();

		for(Entity report : reports) {
			JSONObject jsonReport = new JSONObject();

			jsonReport.put(DSUtils.REPORT, report.getKey().getName());
			jsonReport.put(DSUtils.REPORT_TITLE, report.getProperty(DSUtils.REPORT_TITLE));
			jsonReport.put(DSUtils.REPORT_ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
			jsonReport.put(DSUtils.REPORT_USERNAME, report.getProperty(DSUtils.REPORT_USERNAME));
			jsonReport.put(DSUtils.REPORT_LAT, report.getProperty(DSUtils.REPORT_LAT));
			jsonReport.put(DSUtils.REPORT_LNG, report.getProperty(DSUtils.REPORT_LNG));
			jsonReport.put(DSUtils.REPORT_GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY));
			jsonReport.put(DSUtils.REPORT_STATUS, report.getProperty(DSUtils.REPORT_STATUS));
			jsonReport.put(DSUtils.REPORT_DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION));
			jsonReport.put(DSUtils.REPORT_CREATIONTIMEFORMATTED,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED));
			jsonReport.put(DSUtils.REPORT_PRIVATE, report.getProperty(DSUtils.REPORT_PRIVATE));

			if(withTn) {
				String tn = Storage.getImage(report.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());

				jsonReport.put(DSUtils.REPORT_THUMBNAIL, tn);
			}

			appendVotesAndComments(jsonReport, report);

			array.put(jsonReport);
		}

		return array;
	}

	public static void appendVotesAndComments(JSONObject jsonReport, Entity report) {
		Query commentQuery = new Query(DSUtils.REPORTCOMMENT).setAncestor(report.getKey());

		List<Entity> comments = datastore.prepare(commentQuery).asList(FetchOptions.Builder.withDefaults());

		int numComments = comments.size();

		Query votesQuery = new Query(DSUtils.REPORTVOTES)
				.setAncestor(report.getKey());

		Entity votes;

		try {
			votes = datastore.prepare(votesQuery).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Message.TOO_MANY_RESULTS);
			throw new InternalServerErrorException();
		}

		long numUpvotes = 0;
		long numDownvotes = 0;

		numUpvotes = (long) votes.getProperty(DSUtils.REPORTVOTES_UP);
		numDownvotes = (long) votes.getProperty(DSUtils.REPORTVOTES_DOWN);

		jsonReport.put(DSUtils.REPORT_COMMENTSNUM, numComments);
		jsonReport.put(DSUtils.REPORTVOTES_UP, numUpvotes);
		jsonReport.put(DSUtils.REPORTVOTES_DOWN, numDownvotes);
	}

	private PropertyContainer makeCoordProps(double lat, double lng) {
		PropertyContainer props = new Entity("x", "x");

		String p0lat = doubleToProp(lat, 0);
		String p1lat = doubleToProp(lat, 1);
		String p2lat = doubleToProp(lat, 2);
		String p3lat = doubleToProp(lat, 3);
		String p4lat = doubleToProp(lat, 4);
		String p5lat = doubleToProp(lat, 5);
		String p6lat = doubleToProp(lat, 6);
		String p7lat = doubleToProp(lat, 7);
		String p8lat = doubleToProp(lat, 8);

		String p0lng = doubleToProp(lng, 0);
		String p1lng = doubleToProp(lng, 1);
		String p2lng = doubleToProp(lng, 2);
		String p3lng = doubleToProp(lng, 3);
		String p4lng = doubleToProp(lng, 4);
		String p5lng = doubleToProp(lng, 5);
		String p6lng = doubleToProp(lng, 6);
		String p7lng = doubleToProp(lng, 7);
		String p8lng = doubleToProp(lng, 8);

		props.setProperty(DSUtils.REPORT_P0LAT, p0lat);
		props.setProperty(DSUtils.REPORT_P1LAT, p1lat);
		props.setProperty(DSUtils.REPORT_P2LAT, p2lat);
		props.setProperty(DSUtils.REPORT_P3LAT, p3lat);
		props.setProperty(DSUtils.REPORT_P4LAT, p4lat);
		props.setProperty(DSUtils.REPORT_P5LAT, p5lat);
		props.setProperty(DSUtils.REPORT_P6LAT, p6lat);
		props.setProperty(DSUtils.REPORT_P7LAT, p7lat);
		props.setProperty(DSUtils.REPORT_P8LAT, p8lat);

		props.setProperty(DSUtils.REPORT_P0LNG, p0lng);
		props.setProperty(DSUtils.REPORT_P1LNG, p1lng);
		props.setProperty(DSUtils.REPORT_P2LNG, p2lng);
		props.setProperty(DSUtils.REPORT_P3LNG, p3lng);
		props.setProperty(DSUtils.REPORT_P4LNG, p4lng);
		props.setProperty(DSUtils.REPORT_P5LNG, p5lng);
		props.setProperty(DSUtils.REPORT_P6LNG, p6lng);
		props.setProperty(DSUtils.REPORT_P7LNG, p7lng);
		props.setProperty(DSUtils.REPORT_P8LNG, p8lng);

		return props;
	}

	public String doubleToProp(double d, int precision) {
		String format = "0.";
		if(precision == 0)
			format = "0";
		else
			for(int i = 0; i < precision; i++)
				format += "0";

		DecimalFormat df = new DecimalFormat(format);
		df.setRoundingMode(RoundingMode.HALF_UP);

		return df.format(d);
	}

	private String encodeCacheKey(boolean type, String request, String cursor) {
		String key;
		if(type)
			key = "l";
		else
			key = "r";

		key += request + "|" + cursor;

		return key;
	}

	@DELETE
	@Path("/closedef")
	public Response closeDef() {
		Query query = new Query(DSUtils.REPORT);
		Filter filter = new Query.FilterPredicate(DSUtils.REPORT_STATUS,
				FilterOperator.EQUAL, CLOSED);
		query.setFilter(filter);

		List<Entity> list = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		for(Entity rep : list) {
			Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));
			try {
				Entity closedR = new Entity(DSUtils.CLOSEDREPORT);
				closedR.setPropertiesFrom(rep);
				datastore.put(txn, closedR);
				datastore.delete(txn, rep.getKey());
				txn.commit();
			} catch(Exception e) {
				txn.rollback();
				LOG.info(Message.UNEXPECTED_ERROR + " " + rep.getKey().getName());
			}
		}

		return Response.ok().build();
	}

	// ---------------x--------------- SUBCLASS

	private static final String UP = "up";
	private static final String DOWN = "down";
	private static final String SPAM = "spam";

	@POST
	@Path("/vote/up/{report}")
	public Response upvoteReport(@PathParam (ParamName.REPORT) String report,
			@Context HttpServletRequest request) {
		if(report == null || report == "")
			return Response.status(Status.BAD_REQUEST).build();

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				return upvoteReportRetry(report, username);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response upvoteReportRetry(String report, String username) {
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);

		try {
			Key reportKey = KeyFactory.createKey(DSUtils.REPORT, report);
			Query votesQuery = new Query(DSUtils.REPORTVOTES).setAncestor(reportKey);

			String reporterUsername = datastore.get(reportKey).getProperty(DSUtils.REPORT_USERNAME).toString();
			Key reporterkey = KeyFactory.createKey(DSUtils.USER, reporterUsername);
			Query pointsQuery = new Query(DSUtils.USERPOINTS).setAncestor(reporterkey);

			Entity userPoints;
			Entity vote;

			try {
				vote = datastore.prepare(votesQuery).asSingleEntity();
				userPoints = datastore.prepare(pointsQuery).asSingleEntity();
			} catch(TooManyResultsException e) {
				txn.rollback();
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			long ups = (long) vote.getProperty(DSUtils.REPORTVOTES_UP) + 1;
			long relevance = (long) vote.getProperty(DSUtils.REPORTVOTES_RELEVANCE) + 3;

			vote.setProperty(DSUtils.REPORTVOTES_UP, ups);
			vote.setProperty(DSUtils.REPORTVOTES_RELEVANCE, relevance);


			Entity user = datastore.get(KeyFactory.createKey(DSUtils.USER, username));

			if(user.getProperty(DSUtils.USER_CODE).toString().equals("activated"))
				userPoints.setProperty(DSUtils.USERPOINTS_POINTS,
						(long) userPoints.getProperty(DSUtils.USERPOINTS_POINTS) + 1);

			Entity uservote = new Entity(DSUtils.USERVOTE);
			uservote.setProperty(DSUtils.USERVOTE_USER, username);
			uservote.setProperty(DSUtils.USERVOTE_REPORT, reportKey.getName());
			uservote.setUnindexedProperty(DSUtils.USERVOTE_TYPE, "up");

			List<Entity> entities = Arrays.asList(vote, userPoints, uservote);

			datastore.put(txn, entities);
			txn.commit();
			LOG.info(Message.VOTED_REPORT);
			return Response.ok().build();
		} catch(EntityNotFoundException e) {
			txn.rollback();
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@POST
	@Path("/vote/down/{report}")
	public Response downvoteReport(@PathParam (ParamName.REPORT) String report,
			@Context HttpServletRequest request) {
		if(report == null || report == "")
			return Response.status(Status.BAD_REQUEST).build();

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				return downvoteReportRetry(report, username);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response downvoteReportRetry(String report, String username) {
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);

		try {
			Key reportKey = KeyFactory.createKey(DSUtils.REPORT, report);
			Query votesQuery = new Query(DSUtils.REPORTVOTES).setAncestor(reportKey);

			Entity vote;

			try {
				vote = datastore.prepare(votesQuery).asSingleEntity();
			} catch(TooManyResultsException e) {
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			Entity uservote = new Entity(DSUtils.USERVOTE);
			uservote.setProperty(DSUtils.USERVOTE_USER, username);
			uservote.setProperty(DSUtils.USERVOTE_REPORT, reportKey.getName());
			uservote.setUnindexedProperty(DSUtils.USERVOTE_TYPE, "down");

			vote.setProperty(DSUtils.REPORTVOTES_DOWN,
					(long) vote.getProperty(DSUtils.REPORTVOTES_DOWN) + 1);
			vote.setProperty(DSUtils.REPORTVOTES_RELEVANCE,
					(long) vote.getProperty(DSUtils.REPORTVOTES_RELEVANCE) - 1); 

			List<Entity> entities = Arrays.asList(vote, uservote);

			datastore.put(txn, entities);
			txn.commit();
			LOG.info(Message.VOTED_REPORT);
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
			} catch(InternalServerErrorException e2) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	public Response getVotesRetry(String reportid) {
		Query votesQuery = new Query(DSUtils.REPORTVOTES)
				.setAncestor(KeyFactory.createKey(DSUtils.REPORTVOTES, reportid));

		Entity vote;

		try {
			vote = datastore.prepare(votesQuery).asSingleEntity();
		} catch(TooManyResultsException e) {
			throw new InternalServerErrorException();
		}

		long numUpvotes = 0;
		long numDownvotes = 0;
		long numSpamvotes = 0;

		if(vote != null) {
			numUpvotes = (long) vote.getProperty(DSUtils.REPORTVOTES_UP);
			numDownvotes = (long) vote.getProperty(DSUtils.REPORTVOTES_DOWN);
			numSpamvotes = (long) vote.getProperty(DSUtils.REPORTVOTES_SPAM);
		} else
			throw new InternalServerErrorException();

		JSONObject voteNums = new JSONObject()
				.put(DSUtils.REPORTVOTES_UP, numUpvotes)
				.put(DSUtils.REPORTVOTES_DOWN, numDownvotes)
				.put(DSUtils.REPORTVOTES_SPAM, numSpamvotes);

		LOG.info(Message.VOTED_REPORT);
		return Response.ok(voteNums).build();
	}

	@POST
	@Path("/vote/spam/{report}")
	public Response spamvoteReport(@PathParam (ParamName.REPORT) String report,
			@Context HttpServletRequest request) {
		if(report == null || report == "")
			return Response.status(Status.BAD_REQUEST).build();

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				return spamvoteReportRetry(report, username);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response spamvoteReportRetry(String report, String username) {
		TransactionOptions options = TransactionOptions.Builder.withXG(true);
		Transaction txn = datastore.beginTransaction(options);

		try {
			Key reportKey = KeyFactory.createKey(DSUtils.REPORT, report);
			Query votesQuery = new Query(DSUtils.REPORTVOTES).setAncestor(reportKey);

			String reporterUsername = datastore.get(reportKey).getProperty(DSUtils.REPORT_USERNAME).toString();
			Key reporterkey = KeyFactory.createKey(DSUtils.USER, reporterUsername);
			Query pointsQuery = new Query(DSUtils.USERPOINTS).setAncestor(reporterkey);

			Entity userPoints;
			Entity vote;

			try {
				vote = datastore.prepare(votesQuery).asSingleEntity();
				userPoints = datastore.prepare(pointsQuery).asSingleEntity();
			} catch(TooManyResultsException e) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			long spams = (long) vote.getProperty(DSUtils.REPORTVOTES_SPAM) + 1;
			long ups = (long) vote.getProperty(DSUtils.REPORTVOTES_UP);
			long relevance = (long) vote.getProperty(DSUtils.REPORTVOTES_RELEVANCE) - 4;

			vote.setProperty(DSUtils.REPORTVOTES_SPAM,
					spams);

			vote.setProperty(DSUtils.REPORTVOTES_RELEVANCE, relevance);

			Entity uservote = new Entity(DSUtils.USERVOTE);
			uservote.setProperty(DSUtils.USERVOTE_USER, username);
			uservote.setProperty(DSUtils.USERVOTE_REPORT, reportKey.getName());
			uservote.setUnindexedProperty(DSUtils.USERVOTE_TYPE, "spam");

			List<Entity> entities = Arrays.asList(vote, userPoints, uservote);

			if(spams > ups / 4 || spams > 25) {
				Entity spammedRep = new Entity(DSUtils.SPAMMEDREPORT, reportKey);
				spammedRep.setPropertiesFrom(datastore.get(reportKey));
				entities.add(spammedRep);
			}

			userPoints.setProperty(DSUtils.USERPOINTS_POINTS,
					(long) userPoints.getProperty(DSUtils.USERPOINTS_POINTS) - 3);

			datastore.put(txn, entities);
			LOG.info(Message.VOTED_REPORT);
			return Response.ok().build();

		} catch(EntityNotFoundException e) {
			txn.rollback();
			LOG.info(Message.UNEXPECTED_ERROR);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@POST
	@Path("/vote/multiple")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response voteAll(String votesO, @Context HttpServletRequest request) {
		int retries = 5;

		LOG.info(votesO);

		JSONObject votes = new JSONObject(votesO);

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Iterator<String> keys = votes.keys();

		while(true) {
			try {
				while(keys.hasNext()) {
					String reportid = keys.next();
					String vote = votes.getString(reportid);

					if(vote.equals(UP))
						upvoteReportRetry(reportid, username);
					else if(vote.equals(DOWN))
						downvoteReportRetry(reportid, username);
					else if(vote.equals(SPAM))
						spamvoteReportRetry(reportid, username);
					else
						return Response.status(Status.BAD_REQUEST).build();
				}

				return Response.ok().build();
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	// -----------x----------- SUBCLASS

	@POST
	@Path("/comment/{report}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response commentOnRep(String text, @QueryParam(ParamName.CURSOR) String cursor,
			@Context HttpServletRequest request, @PathParam(ParamName.REPORT) String report) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		JSONObject obj = new JSONObject(text);
		text = obj.getString(DSUtils.REPORTCOMMENT_TEXT);

		int retries = 5;
		while(true) {
			try {
				try {
					datastore.get(KeyFactory.createKey(DSUtils.REPORT, report));
				} catch (EntityNotFoundException e) {
					LOG.info(Message.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				Entity comment = new Entity(DSUtils.REPORTCOMMENT);
				comment.setProperty(DSUtils.REPORTCOMMENT_TEXT, text);
				comment.setProperty(DSUtils.REPORTCOMMENT_TIME, new Date());
				comment.setProperty(DSUtils.REPORTCOMMENT_USER, username);
				comment.setProperty(DSUtils.REPORTCOMMENT_REPORT, report);
				datastore.put(comment);
				return Response.ok().build();
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	@GET
	@Path("/comment/get/{report}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response getComments(@Context HttpServletRequest request,
			@PathParam(ParamName.REPORT) String report, @QueryParam(ParamName.CURSOR) String cursor) {
		int retries = 5;

		while(true) {
			try {
				FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

				if(cursor != null && !cursor.equals(""))
					fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

				Query query = new Query(DSUtils.REPORTCOMMENT);
				Filter filter = new Query
						.FilterPredicate(DSUtils.REPORTCOMMENT_REPORT, 
								FilterOperator.EQUAL, report);
				query.setFilter(filter);

				query.addProjection(new PropertyProjection(DSUtils.REPORTCOMMENT_TEXT, String.class))
				.addProjection(new PropertyProjection(DSUtils.REPORTCOMMENT_TIME, Date.class))
				.addProjection(new PropertyProjection(DSUtils.REPORTCOMMENT_USER, String.class));

				QueryResultList<Entity> list = datastore.prepare(query).asQueryResultList(fetchOptions);

				JSONArray array = new JSONArray();

				for(Entity comment : list) {
					LOG.info("çugiug");
					JSONObject obj = new JSONObject();
					LOG.info("çugiug");
					obj.put(DSUtils.REPORTCOMMENT_TEXT, comment.getProperty(DSUtils.REPORTCOMMENT_TEXT));
					LOG.info("çugiug");
					obj.put(DSUtils.REPORTCOMMENT_TIME, comment.getProperty(DSUtils.REPORTCOMMENT_TIME));
					
					LOG.info("çugiug");

					String username = comment.getProperty(DSUtils.REPORTCOMMENT_USER).toString();
					obj.put(DSUtils.REPORTCOMMENT_USER, username);
					
					LOG.info("çugiug");

					Key user = KeyFactory.createKey(DSUtils.USER, username);
					Entity userOE;
					
					LOG.info("çugiug");
					try {
						Query query2 = new Query(DSUtils.USEROPTIONAL).setAncestor(user);

						userOE = datastore.prepare(query2).asSingleEntity();

						if(userOE == null)
							throw new TooManyResultsException();
					} catch (TooManyResultsException e) {
						LOG.info(Message.UNEXPECTED_ERROR + " " + comment.toString() + " " + username);
						continue;
					}
					
					LOG.info("çugiug");

					Object profpicpath = userOE.getProperty
							(DSUtils.USEROPTIONAL_PICTNPATH);

					if(profpicpath != null) {
						String profpic = Storage.getImage(profpicpath.toString());
						obj.put(DSUtils.USER_PROFPICTN, profpic);
					}

					array.put(obj);
				}
				
				LOG.info("çugiug");

			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}
}
