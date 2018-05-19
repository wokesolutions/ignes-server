package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;
import org.json.JSONObject;

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
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.ReportData;
import com.wokesolutions.ignes.util.Boundaries;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Haversine;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.PropertyValue;
import com.wokesolutions.ignes.util.ReportRequest;
import com.wokesolutions.ignes.util.ParamName;
import com.wokesolutions.ignes.util.Storage;

@Path("/report")
public class Report {

	private static final Logger LOG = Logger.getLogger(Report.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

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
			reportVotes.setProperty(DSUtils.REPORTVOTES_UP, 0L);
			reportVotes.setProperty(DSUtils.REPORTVOTES_DOWN, 0L);

			Entity reportComments = new Entity(DSUtils.REPORT_COMMENTS, reportKey);
			reportComments.setProperty(DSUtils.REPORTCOMMENTS_NUM, 0);

			List<Entity> entities = Arrays.asList(report, reportVotes, reportComments);

			LOG.info(Message.REPORT_CREATED + reportid);
			datastore.put(txn, entities);
			txn.commit();

			if(cache.get(data.report_locality) != null)
				cache.clearAll();

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
			@QueryParam (ParamName.OFFSET) int offset,
			@Context HttpServletRequest request) {
		if(minlat == 0 || minlng == 0 || maxlat == 0 || maxlng == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinBoundariesRetry(minlat, minlng, maxlat, maxlng, offset, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinBoundariesRetry(double minlat, double minlng,
			double maxlat, double maxlng,
			int offset,
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

		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		QueryResultList<Entity> reports =
				datastore.prepare(latlngQuery).asQueryResultList(fetchOptions);

		JSONArray jsonReports;
		JSONArray jsonReportsFull = new JSONArray();

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				String requestId = codeRequestId(
						codeCoordsBoundaries(minlat, minlng, maxlat, maxlng), 0, "");

				jsonReports = buildJsonReports(reports, requestId, offset, jsonReportsFull);

				cache.put(requestId, jsonReportsFull.toString());
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			return Response.ok()
					.entity(jsonReports.toString()).build();
		}
	}

	@GET
	@Path("/getwithinradius")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsWithinRadius(
			@QueryParam(ParamName.LAT) double lat,
			@QueryParam(ParamName.LNG) double lng,
			@QueryParam(ParamName.RADIUS) double radius,
			@QueryParam (ParamName.OFFSET) int offset,
			@Context HttpServletRequest request) {
		if(lat == 0 || lng == 0 || radius == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinRadiusRetry(lat, lng, radius, offset, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinRadiusRetry(double lat, double lng,
			double radius,
			int offset,
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

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		List<Entity> reports =
				datastore.prepare(latlngQuery).asList(FetchOptions.Builder.withDefaults());

		JSONArray jsonReports;
		JSONArray jsonReportsFull = new JSONArray();

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				String requestId = codeRequestId(codeCoordsRadius(lat, lng, radius), 0, "");

				jsonReports = buildJsonReports(reports, requestId, offset, jsonReportsFull);

				cache.put(requestId, jsonReportsFull.toString());
			} catch(DatastoreException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
			return Response.ok()
					.entity(jsonReports.toString()).build();
		}
	}

	@GET
	@Path("/getinlocation")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsInLocation(@QueryParam(ParamName.LOCATION) String location,
			@QueryParam(ParamName.REQUESTID) String requestid,
			@QueryParam(ParamName.OFFSET) int offset,
			@Context HttpServletRequest request) {
		if(location == null || requestid == null || offset < 0)
			return Response.status(Status.EXPECTATION_FAILED).build();

		if(cache.contains(requestid)) {
			LOG.info(Message.USING_CACHE);
			JSONArray reports = new JSONArray(cache.get(requestid).toString());

			int size = reports.length();

			if(offset >= size)
				return Response.status(Status.EXPECTATION_FAILED).build();

			int endset = offset + 10;
			int sendEndset = endset;
			if(endset >= size) {
				endset = size - 1;
				sendEndset = -1;
			}
			
			String jsonReports = "[{" + "\"offset\":" + sendEndset + ", \"requestid\":\""
			+ requestid + "\"}, ";
			
			for(int i = offset; i < endset; i++) {
				jsonReports += reports.getJSONObject(i + 1).toString();
				jsonReports += ", ";
			}
			
			jsonReports = jsonReports.substring(0, jsonReports.length() - 2);
			
			jsonReports += "]";

			return Response.ok().entity(jsonReports).build();
		}

		int retries = 5;
		while(true) {
			try {
				return getReportsInLocationRetry(location, offset, requestid, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsInLocationRetry(String location,
			int offset, String requestid,
			HttpServletRequest request) {
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

		FetchOptions fetchOptions = FetchOptions.Builder.withDefaults();

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		List<Entity> reports =
				datastore.prepare(latlngQuery).asList(fetchOptions);

		JSONArray jsonReports;
		JSONArray jsonReportsFull = new JSONArray();

		if(reports.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			try {
				String requestId = codeRequestId(location, 0, "");

				jsonReports = buildJsonReports(reports, requestId, offset, jsonReportsFull);

				cache.put(requestId, jsonReportsFull.toString());
			} catch(InternalServerErrorException e) {
				LOG.info(Message.REPORT_NOT_FOUND);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			return Response.ok()
					.entity(jsonReports.toString()).build();
		}
	}

	@GET
	@Path("/thumbnails")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getThumbnails(@QueryParam (ParamName.REQUESTID) String requestid,
			@QueryParam (ParamName.OFFSET) int offset,
			@Context HttpServletRequest request) {

		if(requestid == null || offset < 0)
			return Response.status(Status.EXPECTATION_FAILED).build();

		int retries = 5;
		while(true) {
			try {
				return getThumbnailsRetry(requestid, offset, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getThumbnailsRetry(String requestid, int offset, HttpServletRequest request) {
		JSONObject thumbnails = null;
		JSONArray jsonReports = null;

		if(cache.contains(requestid)) {
			jsonReports = new JSONArray((String) cache.get(requestid));
			
			LOG.info(jsonReports.toString());
		} else {
			ReportRequest reportRequest = decodeRequestId(requestid);
			if(reportRequest.type.equals(ReportRequest.L))
				jsonReports = new JSONArray(getReportsInLocationRetry(reportRequest.location, offset,
						requestid, request).getEntity().toString());
			else if(reportRequest.type.equals(ReportRequest.R))
				jsonReports = new JSONArray(getReportsWithinRadiusRetry(reportRequest.lat,
						reportRequest.lng, reportRequest.radius, offset,
						request).getEntity().toString());
			else
				jsonReports = new JSONArray(getReportsWithinBoundariesRetry(reportRequest.minlat,
						reportRequest.minlng, reportRequest.maxlat, reportRequest.maxlng,
						offset, request).getEntity().toString());
		}

		int reportsSize = jsonReports.length();

		int endset = offset + 10;
		if(endset >= reportsSize)
			endset = reportsSize - 1;

		JSONArray subReports = new JSONArray();

		for(int i = offset; i < endset; i++)
			subReports.put(jsonReports.get(i + 1));

		int subReportsSize = subReports.length();

		if(offset >= reportsSize)
			return Response.status(Status.EXPECTATION_FAILED).build();

		List<Key> keys = new ArrayList<Key>(subReportsSize);

		if(endset == reportsSize)
			endset = -1;

		for(Object report : subReports) {
			JSONObject jsonReport = new JSONObject(report.toString());
			Key key = KeyFactory.createKey(DSUtils.REPORT, jsonReport.getString(DSUtils.REPORT));
			keys.add(key);
		}

		Map<Key, Entity> entities = datastore.get(keys);
		Map<String, String> map = new HashMap<String, String>(entities.size() + 2);

		map.put(ParamName.OFFSET, String.valueOf(endset));
		map.put(ParamName.REQUESTID, requestid);

		for(Entry<Key, Entity> report : entities.entrySet()) {
			String thumbnail = Storage.getImage(report.getValue()
					.getProperty(DSUtils.REPORT_THUMBNAILPATH).toString());
			map.put(report.getKey().getName(), thumbnail);
		}

		thumbnails = new JSONObject(map);

		return Response.ok().entity(thumbnails.toString()).build();
	}

	private JSONArray buildJsonReports(List<Entity> reports, String id,
			int offset, JSONArray jsonReportsFull) {
		int endset = offset + 10;
		if(endset > reports.size())
			endset = reports.size();

		if(endset == reports.size())
			endset = -1;

		JSONObject jsonId = new JSONObject()
				.put(ParamName.REQUESTID, id)
				.put(ParamName.OFFSET, endset);

		jsonReportsFull.put(jsonId);

		for(Entity report : reports) {
			JSONObject jsonReport = new JSONObject();
			jsonReport.put(DSUtils.REPORT, report.getKey().getName());
			jsonReport.put(DSUtils.REPORT_LAT, report.getProperty(DSUtils.REPORT_LAT).toString());
			jsonReport.put(DSUtils.REPORT_LNG, report.getProperty(DSUtils.REPORT_LNG).toString());
			jsonReport.put(DSUtils.REPORT_STATUS, report.getProperty(DSUtils.REPORT_STATUS).toString());
			jsonReport.put(DSUtils.REPORT_ADDRESS, report.getProperty(DSUtils.REPORT_ADDRESS).toString());
			jsonReport.put(DSUtils.REPORT_CREATIONTIMEFORMATTED,
					report.getProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED).toString());
			jsonReport.put(DSUtils.REPORT_USERNAME, report.getProperty(DSUtils.REPORT_USERNAME).toString());
			if(report.hasProperty(DSUtils.REPORT_GRAVITY))
				jsonReport.put(DSUtils.REPORT_GRAVITY, report.getProperty(DSUtils.REPORT_GRAVITY).toString());
			if(report.hasProperty(DSUtils.REPORT_DESCRIPTION))
				jsonReport.put(DSUtils.REPORT_DESCRIPTION, report.getProperty(DSUtils.REPORT_DESCRIPTION).toString());
			if(report.hasProperty(DSUtils.REPORT_TITLE))
				jsonReport.put(DSUtils.REPORT_TITLE, report.getProperty(DSUtils.REPORT_TITLE).toString());

			appendVotesAndComments(jsonReport, report);

			jsonReportsFull.put(new JSONObject(jsonReport.toString()));
		}

		JSONArray jsonReports = new JSONArray();

		jsonReports.put(jsonId);

		for(int i = offset; i < endset; i++)
			jsonReports.put(jsonReportsFull.getJSONObject(i + 1));

		return jsonReports;
	}

	private void appendVotesAndComments(JSONObject jsonReport, Entity report) {
		Filter numFilter =
				new Query.FilterPredicate(DSUtils.REPORTCOMMENTS_NUM,
						FilterOperator.GREATER_THAN_OR_EQUAL, 0);

		Query commentQuery = new Query(DSUtils.REPORT_COMMENTS)
				.setFilter(numFilter).setAncestor(report.getKey());

		List<Entity> comment = datastore.prepare(commentQuery).asList(FetchOptions.Builder.withDefaults());

		String numComments = "0";

		if(!comment.isEmpty())
			numComments = comment.get(0).getProperty(DSUtils.REPORTCOMMENTS_NUM).toString();
		else
			throw new InternalServerErrorException();

		Query votesQuery = new Query(DSUtils.REPORT_VOTES)
				.setAncestor(report.getKey());

		List<Entity> votes = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());

		String numUpvotes = "0";
		String numDownvotes = "0";

		if(!votes.isEmpty()) {
			numUpvotes = votes.get(0).getProperty(DSUtils.REPORTVOTES_UP).toString();
			numDownvotes = votes.get(0).getProperty(DSUtils.REPORTVOTES_DOWN).toString();
		} else
			throw new InternalServerErrorException();

		jsonReport.put(DSUtils.REPORT_COMMENTSNUM, numComments);
		jsonReport.put(DSUtils.REPORTVOTES_UP, numUpvotes);
		jsonReport.put(DSUtils.REPORTVOTES_DOWN, numDownvotes);
	}

	private String codeRequestId(String location, int gravity, String user) {
		String l = location;
		String g = "|" + gravity;
		String u = "|" + user;

		return l + g + u;
	}

	private ReportRequest decodeRequestId(String requestid) {
		int first = requestid.indexOf("|");
		int second = requestid.indexOf("|", first);

		String location = requestid.substring(0, first);
		String gravity = requestid.substring(first + 1, second);
		String user = requestid.substring(second + 1, requestid.length());

		ReportRequest request;

		if(location.startsWith(".r")) {
			request = new ReportRequest(ReportRequest.R);
			int firstComma = location.indexOf(",");
			int secondComma = location.indexOf(",", firstComma);
			request.lat = Double.parseDouble(location.substring(2, firstComma));
			request.lng = Double.parseDouble(location.substring(firstComma + 2, secondComma));
			request.radius = Double.parseDouble(location.substring(secondComma + 2, location.length()));
		} else if(location.startsWith(".b")) {
			request = new ReportRequest(ReportRequest.B);
			int firstComma = location.indexOf(",");
			int secondComma = location.indexOf(",", firstComma);
			int thirdComma = location.indexOf(",", secondComma);
			request.minlat = Double.parseDouble(location.substring(2, firstComma));
			request.minlng = Double.parseDouble(location.substring(firstComma + 2, secondComma));
			request.maxlat = Double.parseDouble(location.substring(secondComma + 2, thirdComma));
			request.maxlng = Double.parseDouble(location.substring(thirdComma + 2, location.length()));
		} else {
			request = new ReportRequest(ReportRequest.L);
			request.location = location.substring(2, location.length());
		}

		request.gravity = Integer.parseInt(gravity);
		request.user = user;

		return request;
	}

	private String codeCoordsRadius(double lat, double lng, double radius) {
		return ".r" + Double.toString(lat) + ", " + Double.toString(lng) + ", " + Double.toString(radius);
	}

	private String codeCoordsBoundaries(double lat, double lng, double lat2, double lng2) {
		return ".b" + Double.toString(lat) + ", " + Double.toString(lng)
		+ ", " + Double.toString(lat2) + ", " + Double.toString(lng2);
	}
}
