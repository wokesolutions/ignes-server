package com.wokesolutions.ignes.api;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
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
import com.wokesolutions.ignes.data.AcceptData;
import com.wokesolutions.ignes.data.InfoData;
import com.wokesolutions.ignes.data.ReportData;
import com.wokesolutions.ignes.exceptions.VoteException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Email;
import com.wokesolutions.ignes.util.Haversine;
import com.wokesolutions.ignes.util.Prop;
import com.wokesolutions.ignes.util.ReportVotes;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.ProfanityFilter;
import com.wokesolutions.ignes.util.Storage;
import com.wokesolutions.ignes.util.Storage.StoragePath;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/report")
public class Report {

	private static final Logger LOG = Logger.getLogger(Report.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	// private static final MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
	private static final int BATCH_SIZE = 10;

	private static final ProfanityFilter proffilter = new ProfanityFilter();

	public static final String PORTUGAL = "Portugal";

	public static final String REPORT = "report";
	public static final String COMMENT = "comment";

	public static final String OPEN = "open";
	public static final String CLOSED = "closed";
	public static final String STANDBY = "standby";
	public static final String WIP = "wip";

	private static final String NOT_FOUND = "NOT_FOUND";

	public static final String LAT = "lat";
	public static final String LNG = "lng";

	private static final String OCORRENCIA_RAPIDA = "Ocorrência Rápida";

	private static final long TO_CLOSE = TimeUnit.DAYS.toMillis(3);

	private static final int DEFAULT_GRAVITY = 2;
	private static final int NO_TRUST_GRAVITY = 1;

	public Report() {}

	@POST
	@Path("/create")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response createReport(ReportData data,
			@Context HttpServletRequest request) {
		if(!data.isValid()) {
			LOG.info(data.toString());
			return Response.status(Status.EXPECTATION_FAILED).build();
		}
		int retries = 5;
		while(true) {
			try {
				return createReportRetry(data, request);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
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
		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		LOG.info(Log.ATTEMPT_CREATE_REPORT);

		try {
			username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

			reportid = ReportData.generateId(username, creationtime);

			LOG.info(Log.ATTEMPT_CREATE_REPORT + reportid);
			reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
			datastore.get(reportKey);
			txn.rollback();
			return Response.status(Status.CONFLICT).entity(Log.DUPLICATE_REPORT).build();
		} catch(EntityNotFoundException e1) {
			try {
				reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
				datastore.get(reportKey);
				txn.rollback();
				return Response.status(Status.CONFLICT).entity(Log.DUPLICATE_REPORT).build();
			} catch(EntityNotFoundException e2) {
				if(username == null || reportKey == null) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				Key userKey = KeyFactory.createKey(DSUtils.USER, username);

				Entity report = new Entity(reportKey);

				double[] middle = new double[2];

				if(data.points == null) {
					LOG.info(Log.REPORT_IS_LATLNG);

					report.setProperty(DSUtils.REPORT_LAT, data.lat);
					report.setProperty(DSUtils.REPORT_LNG, data.lng);

					report.setProperty(DSUtils.REPORT_POINTS, null);
				} else {
					LOG.info(Log.REPORT_IS_POINTS);

					JSONArray points = new JSONArray(data.points);
					LOG.info(points.toString());
					middle = ReportData.calcMiddle(points);

					report.setProperty(DSUtils.REPORT_LAT, middle[0]);
					report.setProperty(DSUtils.REPORT_LNG, middle[1]);

					report.setProperty(DSUtils.REPORT_POINTS, data.points);
				}

				SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				sdf.setTimeZone(TimeZone.getTimeZone(PORTUGAL));

				report.setProperty(DSUtils.REPORT_CREATIONTIME, creationtime);
				report.setProperty(DSUtils.REPORT_PRIVATE, data.isprivate);
				report.setProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED, sdf.format(creationtime));
				report.setProperty(DSUtils.REPORT_USER, userKey);
				report.setProperty(DSUtils.REPORT_CATEGORY, data.category);

				Entity user;
				try {
					user = datastore.get(userKey);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.USER_NOT_FOUND);
					txn.rollback();
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				String level = user.getProperty(DSUtils.USER_LEVEL).toString();

				if(level.equals(UserLevel.LEVEL1))
					report.setProperty(DSUtils.REPORT_STATUS, STANDBY);
				else
					report.setProperty(DSUtils.REPORT_STATUS, OPEN);

				report.setUnindexedProperty(DSUtils.REPORT_CREATIONLATLNG,
						request.getHeader(CustomHeader.APPENGINE_LATLNG));

				if(data.gravity >= 1 && data.gravity <= 5) {
					report.setProperty(DSUtils.REPORT_GRAVITY, data.gravity);

					if(data.gravity == 5 && level.equals(UserLevel.LEVEL2))
						report.setProperty(DSUtils.REPORT_STATUS, STANDBY);
				} else {
					int gravity = DEFAULT_GRAVITY;
					if(user.getProperty(DSUtils.USER_LEVEL).equals(UserLevel.LEVEL1))
						gravity = NO_TRUST_GRAVITY;

					report.setProperty(DSUtils.REPORT_GRAVITY, gravity);
				}

				if(data.address != null)
					report.setProperty(DSUtils.REPORT_ADDRESS, data.address);

				if(data.title != null && !data.title.equals(""))
					report.setProperty(DSUtils.REPORT_TITLE, proffilter.filter(data.title));
				else
					report.setProperty(DSUtils.REPORT_TITLE, OCORRENCIA_RAPIDA);

				if(data.description != null)
					report.setProperty(DSUtils.REPORT_DESCRIPTION, proffilter.filter(data.description));
				else
					report.setProperty(DSUtils.REPORT_DESCRIPTION, null);

				if(data.locality != null)
					report.setProperty(DSUtils.REPORT_LOCALITY, data.locality);
				if(data.city != null)
					report.setProperty(DSUtils.REPORT_DISTRICT, data.city);

				LinkedList<String> folders = new LinkedList<String>();
				folders.add(Storage.IMG_FOLDER);
				folders.add(Storage.REPORT_FOLDER);
				StoragePath pathImg = new StoragePath(folders, reportid);
				if(!Storage.saveImage(data.img, pathImg,
						data.imgwidth, data.imgheight, data.imgorientation, true)) {
					LOG.info(Log.STORAGE_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}

				report.setProperty(DSUtils.REPORT_IMGPATH, pathImg.makePath());
				report.setProperty(DSUtils.REPORT_THUMBNAILPATH, pathImg.makeTnPath());

				Entity reportVotes = new Entity(DSUtils.REPORTVOTES, reportid, report.getKey());
				reportVotes.setProperty(DSUtils.REPORTVOTES_UP, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_DOWN, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_DOWN, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_SPAM, 0L);
				reportVotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE, 0);

				if(data.points == null)
					report.setPropertiesFrom(makeCoordProps(data.lat, data.lng));
				else
					report.setPropertiesFrom(makeCoordProps(middle[0], middle[1]));

				List<Entity> entities = Arrays.asList(report, reportVotes);

				LOG.info(Log.REPORT_CREATED + reportid);
				datastore.put(txn, entities);
				txn.commit();

				/*cache.delete(data.locality);

				if(data.lat == 0 && data.lng == 0)
					cache.delete(coordsCacheKey(middle[0], middle[1]));
				else
					cache.delete(coordsCacheKey(data.lat, data.lng));*/

				return Response.ok().build();
			} catch(Exception e) {
				LOG.info(e.toString());
				LOG.info(e.getMessage());
				return null;
			}
		} finally {
			if(txn.isActive()) {
				LOG.info(Log.TXN_ACTIVE);
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
			LOG.info(Log.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONObject obj = new JSONObject();
		try {
			String tn = Storage.getImage(rep.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());
			obj.put(Prop.THUMBNAIL, tn);

			LOG.info(tn);
		} catch(Exception e) {
			LOG.info(Log.REPORT_NOT_FOUND);
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

		String cacheKey = coordsCacheKey(lat, lng);

		/*if(cache.contains(cacheKey)) {
			LOG.info(Log.USING_CACHE);
			LOG.info(cacheKey);

			String cacheKeyNum = cacheKey + "#";
			if(cache.increment(cacheKeyNum, 1L) == null)
				cache.put(cacheKeyNum, 0, Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(30)));

			return Response.ok().entity(cache.get(cacheKey)).build();
		}*/

		int retries = 5;
		while(true) {
			try {
				return getReportsWithinRadiusRetry(lat, lng, radius, cursor, cacheKey);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinRadiusRetry(double lat, double lng,
			double radius, String cursor, String cacheKey) {
		LOG.info(Log.ATTEMPT_GIVE_ALL_REPORTS);

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

		LOG.info(Log.SEARCHING_IN_COORDS + pPropLat + " - " + latValue +
				" | " + pPropLng + " - " + lngValue);

		reportQuery
		.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USER, Key.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CATEGORY, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_POINTS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_DESCRIPTION, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));

		QueryResultList<Entity> reports =
				datastore.prepare(reportQuery).asQueryResultList(fetchOptions);

		cursor = reports.getCursor().toWebSafeString();

		JSONArray jsonReports;

		if(reports.isEmpty() && (cursor == null || cursor.equals("")))
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				jsonReports = buildJsonReports(reports, false);
			} catch(DatastoreException e) {
				LOG.info(Log.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			/*String cacheKeyNum = cacheKey + "#";
			if(cache.get(cacheKeyNum) == null)
				cache.put(cacheKeyNum, "0", Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(30)));
			else
				cache.put(cacheKeyNum, Long.parseLong(cache.get(cacheKeyNum).toString()) + 1L);

			if(Long.parseLong(cache.get(cacheKeyNum).toString()) >= 5L)
				cache.put(cacheKey, jsonReports.toString());*/

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

		/*if(cache.contains(location)) {
			LOG.info(Log.USING_CACHE);

			String cacheKeyNum = location + "#";
			if(cache.get(cacheKeyNum) == null)
				cache.put(cacheKeyNum, "0", Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(30)));
			else
				cache.put(cacheKeyNum, Long.parseLong(cache.get(cacheKeyNum).toString()) + 1L);

			return Response.ok().entity(cache.get(location)).build();
		}*/

		int retries = 5;
		while(true) {
			try {
				return getReportsInLocationRetry(location, cursor, location);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsInLocationRetry(String location, String cursor, String cacheKey) {
		LOG.info(Log.ATTEMPT_GIVE_ALL_REPORTS);

		Filter localityFilter =
				new Query.FilterPredicate(DSUtils.REPORT_LOCALITY,
						FilterOperator.EQUAL, location);

		FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

		if(cursor != null && !cursor.equals(""))
			fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		Query reportQuery = new Query(DSUtils.REPORT).setFilter(localityFilter);

		reportQuery.addProjection(new PropertyProjection(DSUtils.REPORT_TITLE, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_ADDRESS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_USER, Key.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_GRAVITY, Integer.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LAT, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_LNG, Double.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_POINTS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_STATUS, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_DESCRIPTION, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CATEGORY, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_CREATIONTIMEFORMATTED, String.class))
		.addProjection(new PropertyProjection(DSUtils.REPORT_PRIVATE, Boolean.class));

		QueryResultList<Entity> reports =
				datastore.prepare(reportQuery).asQueryResultList(fetchOptions);

		JSONArray jsonReports;

		if(reports.isEmpty() && (cursor == null || cursor.equals("")))
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				jsonReports = buildJsonReports(reports, false);
			} catch(InternalServerErrorException e) {
				LOG.info(Log.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			/*String cacheKeyNum = cacheKey + "#";
			if(cache.increment(cacheKeyNum, 1L) == null)
				cache.put(cacheKeyNum, 0L, Expiration.byDeltaSeconds((int) TimeUnit.MINUTES.toSeconds(30)));

			if(Long.parseLong(cache.get(cacheKeyNum).toString()) >= 5L)
				cache.put(cacheKey, jsonReports.toString());*/

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
			LOG.info(Log.NO_REPORTS_IN_HEADER);
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
			LOG.info(Log.TOO_MANY_REPORTS);
			return Response.status(Status.BAD_REQUEST).build();
		}

		JSONObject thumbnails = new JSONObject();

		for(String id : ids) {
			Entity report;

			try {
				report = datastore.get(KeyFactory.createKey(DSUtils.REPORT, id));
			} catch (EntityNotFoundException e) {
				LOG.info(Log.REPORT_NOT_FOUND + " - " + id);

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

	public static JSONArray buildJsonReports(QueryResultList<Entity> reports, boolean withTn) {
		JSONArray array = new JSONArray();

		for(Entity report : reports) {
			JSONObject jsonReport = new JSONObject();

			jsonReport.put(Prop.REPORT, report.getKey().getName());
			jsonReport.put(Prop.TITLE, report.getProperty(DSUtils.REPORT_TITLE));
			jsonReport.put(Prop.ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS));
			jsonReport.put(Prop.USERNAME,
					((Key) report.getProperty(DSUtils.REPORT_USER)).getName());

			Object points = report.getProperty(DSUtils.REPORT_POINTS);
			if(points != null) {
				jsonReport.put(Prop.POINTS, points);
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

			if(withTn) {
				String tn = Storage.getImage(report.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());

				jsonReport.put(Prop.THUMBNAIL, tn);
			}

			appendVotesAndComments(jsonReport, report);

			array.put(jsonReport);
		}

		return array;
	}

	public static void appendVotesAndComments(JSONObject jsonReport, Entity report) {
		Query commentQuery = new Query(DSUtils.REPORTCOMMENT);
		Filter filter = new Query.FilterPredicate(DSUtils.REPORTCOMMENT_REPORT,
				FilterOperator.EQUAL, report.getKey().getName());
		commentQuery.setFilter(filter);

		List<Entity> comments = datastore.prepare(commentQuery).asList(FetchOptions.Builder.withDefaults());

		int numComments = comments.size();

		Query votesQuery = new Query(DSUtils.REPORTVOTES)
				.setAncestor(report.getKey());

		Entity votes;

		try {
			votes = datastore.prepare(votesQuery).asSingleEntity();
		} catch(TooManyResultsException e) {
			LOG.info(Log.TOO_MANY_RESULTS);
			throw new InternalServerErrorException();
		}

		long numUpvotes = 0;
		long numDownvotes = 0;

		numUpvotes = (long) votes.getProperty(DSUtils.REPORTVOTES_UP);
		numDownvotes = (long) votes.getProperty(DSUtils.REPORTVOTES_DOWN);

		jsonReport.put(Prop.COMMENTS, numComments);
		jsonReport.put(Prop.UPS, numUpvotes);
		jsonReport.put(Prop.DOWNS, numDownvotes);
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

	private String coordsCacheKey(double lat, double lng) {
		DecimalFormat df = new DecimalFormat("#.#");
		df.setRoundingMode(RoundingMode.DOWN);

		String newlat = df.format(lat);
		String newlng = df.format(lng);

		LOG.info(newlat);
		LOG.info(newlng);

		int hash = 13;

		hash = hash * 17 + newlat.hashCode();
		hash = hash * 17 + newlng.hashCode();

		return Integer.toString(hash);
	}

	@GET
	@Path("/getapplications/{report}")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response applications(@Context HttpServletRequest request,
			@PathParam(ParamName.REPORT) String report) {
		int retries = 5;
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				Key reportK = KeyFactory.createKey(DSUtils.REPORT, report);
				Entity reportE;
				try {
					reportE = datastore.get(reportK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				Key userK = KeyFactory.createKey(DSUtils.USER, username);
				if(!reportE.getProperty(DSUtils.REPORT_USER).equals(userK)) {
					LOG.info(Log.REPORT_IS_PRIVATE);
					return Response.status(Status.FORBIDDEN).build();
				}

				Filter applicationF = new Query.FilterPredicate(DSUtils.APPLICATION_REPORT,
						FilterOperator.EQUAL, reportK);
				Query applicationQ = new Query(DSUtils.APPLICATION).setFilter(applicationF);
				List<Entity> applications = datastore.prepare(applicationQ)
						.asList(FetchOptions.Builder.withDefaults());

				JSONArray array = new JSONArray();

				for(Entity application : applications) {
					JSONObject obj = new JSONObject();

					Entity org;
					try {
						org = datastore.get(application.getParent());
					} catch(EntityNotFoundException e) {
						LOG.info(Log.UNEXPECTED_ERROR);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					Entity user;
					try {
						user = datastore.get(org.getParent());
					} catch(EntityNotFoundException e) {
						LOG.info(Log.USER_NOT_FOUND);
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}

					obj.put(Prop.NIF, org.getKey().getName());
					obj.put(Prop.BUDGET, application.getProperty(DSUtils.APPLICATION_BUDGET));
					obj.put(Prop.PHONE, org.getProperty(DSUtils.ORG_PHONE));
					obj.put(Prop.EMAIL, user.getProperty(DSUtils.USER_EMAIL));
					obj.put(Prop.INFO, application.getProperty(DSUtils.APPLICATION_INFO));
					obj.put(Prop.NAME, org.getProperty(DSUtils.ORG_NAME));

					array.put(obj);
				}

				return Response.ok(array.toString()).build();
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	@GET
	@Path("/closedef")
	public Response closeDef() {
		Query query = new Query(DSUtils.REPORT);
		Filter filter = new Query.FilterPredicate(DSUtils.REPORT_STATUS,
				FilterOperator.EQUAL, CLOSED);
		query.setFilter(filter);

		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		List<Entity> list = datastore.prepare(query).asList(fetchOptions);

		for(Entity rep : list) {
			long closetime = ((Date) rep.getProperty(DSUtils.REPORT_CLOSETIME)).getTime();
			long currtime = System.currentTimeMillis();
			if(currtime - closetime < TO_CLOSE)
				continue;

			Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));
			try {
				Entity closedR = new Entity(DSUtils.CLOSEDREPORT);
				closedR.setPropertiesFrom(rep);

				Query taskQ = new Query(DSUtils.TASK).setAncestor(rep.getKey()).setKeysOnly();
				List<Entity> tasks = datastore.prepare(taskQ).asList(fetchOptions);
				List<Key> taskKeys = new ArrayList<Key>(tasks.size());
				for(Entity task : tasks)
					taskKeys.add(task.getKey());

				datastore.put(txn, closedR);
				datastore.delete(txn, rep.getKey());
				datastore.delete(txn, taskKeys);
				txn.commit();
			} catch(Exception e) {
				txn.rollback();
				LOG.info(Log.UNEXPECTED_ERROR + " " + rep.getKey().getName());
			}
		}

		return Response.ok().build();
	}

	@POST
	@Path("/close/{report}")
	public Response closeReport(@Context HttpServletRequest request,
			@PathParam(ParamName.REPORT) String report) {
		int retries = 5;

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				Entity reportE;
				try {
					reportE = datastore.get(KeyFactory.createKey(DSUtils.REPORT, report));
				} catch(EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				Key userK = KeyFactory.createKey(DSUtils.USER, username);
				Entity user;
				try {
					user = datastore.get(userK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.FORBIDDEN).build();
				}

				String userlevel = user.getProperty(DSUtils.USER_LEVEL).toString();

				Transaction txn = datastore.beginTransaction(TransactionOptions
						.Builder.withXG(true));
				
				String orgname = "";

				try {
					if(userlevel.equals(UserLevel.WORKER)) {
						Key workerK = KeyFactory.createKey(userK, DSUtils.WORKER, username);
						Query taskQuery = new Query(DSUtils.TASK).setAncestor(reportE.getKey());
						Filter workerFilter = new Query.FilterPredicate(DSUtils.TASK_WORKER,
								FilterOperator.EQUAL, workerK);
						taskQuery.setFilter(workerFilter);

						Entity task;
						try {
							task = datastore.prepare(taskQuery).asSingleEntity();
						} catch(TooManyResultsException e) {
							LOG.info(Log.UNEXPECTED_ERROR);
							txn.rollback();
							return Response.status(Status.INTERNAL_SERVER_ERROR).build();
						}

						if(task == null) {
							LOG.info(Log.WORKER_NOT_ALLOWED);
							txn.rollback();
							return Response.status(Status.FORBIDDEN).build();
						}
						
						Entity worker;
						try {
							worker = datastore.get(workerK);
						} catch(EntityNotFoundException e) {
							LOG.info(Log.WORKER_NOT_FOUND);
							txn.rollback();
							return Response.status(Status.EXPECTATION_FAILED).build();
						}
						
						Entity org;
						try {
							org = datastore.get((Key) worker.getProperty(DSUtils.WORKER_ORG));
						} catch(EntityNotFoundException e) {
							LOG.info(Log.ORG_NOT_FOUND);
							txn.rollback();
							return Response.status(Status.EXPECTATION_FAILED).build();
						}
						
						orgname = org.getProperty(DSUtils.ORG_NAME).toString();
					} else if(Arrays.asList(UserLevel.LEVEL1, UserLevel.LEVEL2)
							.contains(userlevel)) {
						Key reporter = (Key) reportE.getProperty(DSUtils.REPORT_USER);
						if(!reporter.equals(user.getKey())) {
							txn.rollback();
							LOG.info(Log.NOT_REPORTER);
							return Response.status(Status.FORBIDDEN).build();
						}
					}

					Date date = new Date();

					Entity reportStatusLog = new Entity(DSUtils.REPORTSTATUSLOG, reportE.getKey());
					reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_NEWSTATUS, CLOSED);
					reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_OLDSTATUS,
							reportE.getProperty(DSUtils.REPORT_STATUS));
					reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_TIME, date);
					reportStatusLog.setProperty(DSUtils.REPORTSTATUSLOG_USER, user.getKey());

					reportE.setProperty(DSUtils.REPORT_STATUS, CLOSED);
					reportE.setProperty(DSUtils.REPORT_CLOSETIME, date);

					datastore.put(txn, Arrays.asList(reportStatusLog, reportE));
					LOG.info(Log.REPORT_CLOSED);
					txn.commit();

					Key reporterK = (Key) reportE.getProperty(DSUtils.REPORT_USER);
					Entity reporter;
					try {
						reporter = datastore.get(reporterK);
					} catch(EntityNotFoundException e) {
						LOG.info(Log.USER_NOT_FOUND);
						return Response.status(Status.NOT_FOUND).build();
					}

					if((boolean) reporter.getProperty(DSUtils.USER_SENDEMAIL))
						Email.sendClosedReport(reporter.getProperty(DSUtils.USER_EMAIL).toString(),
								user, orgname, reportE.getProperty(DSUtils.REPORT_TITLE).toString());

					return Response.ok().build();
				} finally {
					if(txn.isActive()) {
						LOG.info(Log.TXN_ACTIVE);
						txn.rollback();
						return Response.status(Status.INTERNAL_SERVER_ERROR).build();
					}
				}
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	@POST
	@Path("/acceptapplication")
	public Response accept(@Context HttpServletRequest request, AcceptData data) {
		if(!data.isValid())
			return Response.status(Status.BAD_REQUEST).build();

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				return acceptRetry(username, data);
			} catch(DatastoreException e) {
				if(retries == 0) {
					LOG.warning(Log.TOO_MANY_RETRIES);
					return Response.status(Status.REQUEST_TIMEOUT).build();
				}

				retries--;
			}
		}
	}

	public Response acceptRetry(String username, AcceptData data) {
		Key userK = KeyFactory.createKey(DSUtils.USER, username);
		Key reportK = KeyFactory.createKey(DSUtils.REPORT, data.report);

		Entity report;
		try {
			report = datastore.get(reportK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.REPORT_NOT_FOUND);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}

		Entity user;
		try {
			user = datastore.get(userK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}

		boolean isprivate = (boolean) report.getProperty(DSUtils.REPORT_PRIVATE);
		String userlevel = user.getProperty(DSUtils.USER_LEVEL).toString();

		if(userlevel.equals(UserLevel.ADMIN) && isprivate
				|| !userlevel.equals(UserLevel.ADMIN) && !isprivate) {
			LOG.info(Log.FORBIDDEN);
			return Response.status(Status.FORBIDDEN).build();
		}

		Key reporterK = (Key) report.getProperty(DSUtils.REPORT_USER);
		if(!reporterK.equals(userK) && isprivate == true) {
			LOG.info(Log.NOT_REPORTER);
			return Response.status(Status.FORBIDDEN).build();
		}

		Key orgUK = KeyFactory.createKey(DSUtils.USER, data.nif);
		Key orgK = KeyFactory.createKey(orgUK, DSUtils.ORG, data.nif);

		LOG.info(orgK.toString());

		Filter applicationF = new Query.FilterPredicate(DSUtils.APPLICATION_REPORT,
				FilterOperator.EQUAL, reportK);
		Query applicationQ = new Query(DSUtils.APPLICATION).setKeysOnly().setFilter(applicationF);

		List<Entity> applications = datastore.prepare(applicationQ)
				.asList(FetchOptions.Builder.withDefaults());

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		for(Entity application : applications)
			datastore.delete(txn, application.getKey());

		Entity task = new Entity(DSUtils.ORGTASK, reportK.getName(), reportK);
		task.setProperty(DSUtils.ORGTASK_ORG, orgK);
		task.setProperty(DSUtils.ORGTASK_TIME, new Date());
		task.setProperty(DSUtils.ORGTASK_USER, userK);

		datastore.put(txn, task);

		txn.commit();
		return Response.ok().build();
	}

	@POST
	@Path("/delete/{report}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response deleteReport(@Context HttpServletRequest request,
			@PathParam(ParamName.REPORT) String report, InfoData info) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				Key userK = KeyFactory.createKey(DSUtils.USER, username);
				Entity user;
				try {
					user = datastore.get(userK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.USER_NOT_FOUND);
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				String userlevel = user.getProperty(DSUtils.USER_LEVEL).toString();

				if(userlevel.equals(UserLevel.ADMIN)
						&& (info == null || info.info == null || info.info.equals(""))) {
					LOG.info(Log.BAD_FORMAT);
					return Response.status(Status.BAD_REQUEST).build();
				}

				Key reportK = KeyFactory.createKey(DSUtils.REPORT, report);
				Entity reportE;
				try {
					reportE = datastore.get(reportK);
				} catch (EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				List<Entity> toDelete = hasPermissionToDelete(reportE, user);

				if(toDelete == null) {
					LOG.info(Log.FORBIDDEN);
					return Response.status(Status.FORBIDDEN).build();
				}

				Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

				if(user.getProperty(DSUtils.USER_LEVEL).equals(UserLevel.ADMIN)
						&& !user.getProperty(DSUtils.REPORT_USER).equals(user.getKey())) {
					Key pointsK = KeyFactory.createKey(user.getKey(), DSUtils.USERPOINTS, username);
					Entity points;
					try {
						points = datastore.get(pointsK);
					} catch (EntityNotFoundException e) {
						LOG.info(Log.UNEXPECTED_ERROR);
						txn.rollback();
						return Response.status(Status.EXPECTATION_FAILED).build();
					}

					points.setProperty(DSUtils.USERPOINTS_POINTS,
							(long) points.getProperty(DSUtils.USERPOINTS_POINTS) - 1);

					datastore.put(txn, points);
				}

				for(Entity del : toDelete)
					LOG.info(del.getKey().getName());

				for(Entity deleted : toDelete)
					datastore.delete(txn, deleted.getKey());

				Entity deletionLog = new Entity(DSUtils.DELETIONLOG, username, userK);
				deletionLog.setUnindexedProperty(DSUtils.DELETIONLOG_DELETED, reportK);
				deletionLog.setUnindexedProperty(DSUtils.DELETIONLOG_INFO, info.info);
				deletionLog.setUnindexedProperty(DSUtils.DELETIONLOG_TIME, new Date());
				deletionLog.setProperty(DSUtils.DELETIONLOG_TYPE, COMMENT);

				datastore.put(txn, deletionLog);

				txn.commit();
				return Response.ok().build();
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private List<Entity> hasPermissionToDelete(Entity report, Entity user) {
		String status = report.getProperty(DSUtils.REPORT_STATUS).toString();
		Key reporterK = (Key) report.getProperty(DSUtils.REPORT_USER);
		String level = user.getProperty(DSUtils.USER_LEVEL).toString();

		LOG.info(status);

		if(!(status.equals(Report.OPEN) || status.equals(Report.STANDBY)))
			return null;

		LOG.info(reporterK.toString() + " " + user.getKey().toString());
		LOG.info(level);

		if(!reporterK.equals(user.getKey()) && !level.equals(UserLevel.ADMIN))
			return null;

		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		Query orgtaskQ = new Query(DSUtils.ORGTASK).setAncestor(report.getKey()).setKeysOnly();
		List<Entity> orgtasks = datastore.prepare(orgtaskQ).asList(fetchOptions);

		if(!level.equals(UserLevel.ADMIN) && !orgtasks.isEmpty())
			return null;

		List<Entity> toDelete = new LinkedList<Entity>();

		Query applicationQ = new Query(DSUtils.APPLICATION)
				.setAncestor(report.getKey()).setKeysOnly();
		List<Entity> applications = datastore.prepare(applicationQ).asList(fetchOptions);

		toDelete.addAll(applications);

		if(!orgtasks.isEmpty())
			for(Entity orgtask : orgtasks) {
				toDelete.add(orgtask);

				Query taskQ = new Query(DSUtils.TASK)
						.setAncestor(orgtask.getKey()).setKeysOnly();
				List<Entity> tasks = datastore.prepare(taskQ).asList(fetchOptions);

				toDelete.addAll(tasks);
			}

		toDelete.add(report);

		Key repPointsK = KeyFactory.createKey(report.getKey(), DSUtils.REPORTVOTES, report.getKey().getName());

		try {
			toDelete.add(datastore.get(repPointsK));
		} catch (EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			return null;
		}

		return toDelete;
	}

	// ---------------x--------------- SUBCLASS

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
				try {
					ReportVotes.vote(ReportVotes.UP, report, username);
				} catch (VoteException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
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
				try {
					ReportVotes.vote(ReportVotes.DOWN, report, username);
				} catch (VoteException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
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
				try {
					ReportVotes.vote(ReportVotes.SPAM, report, username);
				} catch (VoteException e) {
					LOG.info(Log.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	@GET
	@Path("/vote/get/{report}")
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

		LOG.info(Log.VOTED_REPORT);
		return Response.ok(voteNums).build();
	}

	@POST
	@Path("/vote/multiple")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response voteAll(String votesO, @Context HttpServletRequest request) {
		int retries = 5;

		JSONObject votes = new JSONObject(votesO);

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		Iterator<String> keys = votes.keys();

		while(true) {
			try {
				while(keys.hasNext()) {
					String reportid = keys.next();
					String vote = votes.getString(reportid);

					try {
						ReportVotes.vote(vote, reportid, username);
					} catch (VoteException e) {
						LOG.info(Log.UNEXPECTED_ERROR + " " + reportid);
						continue;
					}
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
	@Path("/comment/post/{report}")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response commentOnRep(String text, @QueryParam(ParamName.CURSOR) String cursor,
			@Context HttpServletRequest request, @PathParam(ParamName.REPORT) String report) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		JSONObject obj = new JSONObject(text);
		text = obj.getString(Prop.TEXT);

		int retries = 5;
		while(true) {
			try {

				Key userK = KeyFactory.createKey(DSUtils.USER, username);

				try {
					datastore.get(userK);
				} catch (EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				Date date = new Date();

				Entity comment = new Entity(DSUtils.REPORTCOMMENT);
				comment.setProperty(DSUtils.REPORTCOMMENT_TEXT, proffilter.filter(text));
				comment.setProperty(DSUtils.REPORTCOMMENT_TIME, date);
				comment.setProperty(DSUtils.REPORTCOMMENT_TIMEFORMATTED, 
						new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(date));
				comment.setProperty(DSUtils.REPORTCOMMENT_USER, userK);
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
				try {
					datastore.get(KeyFactory.createKey(DSUtils.REPORT, report));
				} catch (EntityNotFoundException e1) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.NOT_FOUND).build();
				}

				FetchOptions fetchOptions = FetchOptions.Builder.withLimit(BATCH_SIZE);

				if(cursor != null && !cursor.equals(""))
					fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

				Query query = new Query(DSUtils.REPORTCOMMENT);
				Filter filter = new Query
						.FilterPredicate(DSUtils.REPORTCOMMENT_REPORT, 
								FilterOperator.EQUAL, report);
				query.setFilter(filter);

				query.addProjection(new PropertyProjection(DSUtils.REPORTCOMMENT_TEXT, String.class))
				.addProjection(new PropertyProjection(DSUtils.REPORTCOMMENT_TIMEFORMATTED,
						String.class))
				.addProjection(new PropertyProjection(DSUtils.REPORTCOMMENT_USER, Key.class));

				QueryResultList<Entity> list = datastore.prepare(query)
						.asQueryResultList(fetchOptions);

				JSONArray array = new JSONArray();

				for(Entity comment : list) {
					JSONObject obj = new JSONObject();
					obj.put(Prop.COMMENT, Long.toString(comment.getKey().getId()));
					obj.put(Prop.TEXT, comment.getProperty(DSUtils.REPORTCOMMENT_TEXT));
					obj.put(Prop.CREATIONTIME,
							comment.getProperty(DSUtils.REPORTCOMMENT_TIMEFORMATTED));

					String username = ((Key) comment
							.getProperty(DSUtils.REPORTCOMMENT_USER)).getName();
					obj.put(Prop.USERNAME, username);

					Key user = KeyFactory.createKey(DSUtils.USER, username);
					Entity userOE;

					try {
						Query query2 = new Query(DSUtils.USEROPTIONAL).setAncestor(user);

						userOE = datastore.prepare(query2).asSingleEntity();

						if(userOE == null) {
							LOG.info(Log.UNEXPECTED_ERROR + " " + comment.toString() + " " + username);
							continue;
						}
					} catch (TooManyResultsException e) {
						LOG.info(Log.UNEXPECTED_ERROR + " " + comment.toString() + " " + username);
						continue;
					}

					Object profpicpath = userOE.getProperty
							(DSUtils.USEROPTIONAL_PICPATH);

					if(profpicpath != null) {
						String profpic = Storage.getImage(profpicpath.toString());
						obj.put(Prop.PROFPIC, profpic);
					}

					array.put(obj);
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

	@DELETE
	@Path("/comment/delete/{comment}")
	public Response deleteComment(@Context HttpServletRequest request,
			@PathParam(ParamName.COMMENT) String comment) {
		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		int retries = 5;
		while(true) {
			try {
				Key userK = KeyFactory.createKey(DSUtils.USER, username);
				Entity user;
				try {
					user = datastore.get(userK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.USER_NOT_FOUND);
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				Key commentK = KeyFactory.createKey(DSUtils.REPORTCOMMENT, Long.parseLong(comment));
				Entity commentE;
				try {
					commentE = datastore.get(commentK);
				} catch(EntityNotFoundException e) {
					LOG.info(Log.REPORT_NOT_FOUND);
					return Response.status(Status.EXPECTATION_FAILED).build();
				}

				if(!commentE.getProperty(DSUtils.REPORTCOMMENT_USER).equals(userK)
						&& !user.getProperty(DSUtils.USER_LEVEL).equals(UserLevel.ADMIN)) {
					LOG.info(Log.FORBIDDEN);
					return Response.status(Status.FORBIDDEN).build();
				}

				Entity deletionLog = new Entity(DSUtils.DELETIONLOG, username, userK);
				deletionLog.setUnindexedProperty(DSUtils.DELETIONLOG_DELETED, commentK);
				deletionLog.setUnindexedProperty(DSUtils.DELETIONLOG_TIME, new Date());
				deletionLog.setProperty(DSUtils.DELETIONLOG_TYPE, REPORT);

				Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

				datastore.put(txn, deletionLog);

				datastore.delete(txn, commentK);

				txn.commit();

				return Response.ok().build();
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}
}
