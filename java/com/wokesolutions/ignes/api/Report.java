package com.wokesolutions.ignes.api;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
			report.setProperty(DSUtils.REPORT_CREATIONTIMEFORMATTED,
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
				report.setProperty(DSUtils.REPORT_DESCRIPTION, data.report_description);

			String imgid = Storage.IMG_FOLDER + Storage.REPORT_FOLDER + reportid;
			if(!Storage.saveImage(data.report_img, Storage.BUCKET, imgid))
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Message.STORAGE_ERROR).build();

			report.setProperty(DSUtils.REPORT_IMG, imgid);
			
			String thumbnailid = Storage.IMG_FOLDER + Storage.REPORT_FOLDER + Storage.THUMBNAIL_FOLDER + reportid;
			if(!Storage.saveImage(data.report_thumbnail, Storage.BUCKET, thumbnailid))
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Message.STORAGE_ERROR).build();

			report.setProperty(DSUtils.REPORT_THUMBNAIL, thumbnailid);

			Entity reportVotes = new Entity(DSUtils.REPORT_VOTES, reportKey);
			reportVotes.setProperty(DSUtils.REPORT_VOTES_UP, 0L);
			reportVotes.setProperty(DSUtils.REPORT_VOTES_DOWN, 0L);

			List<Entity> entities = Arrays.asList(report, reportVotes);

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
			double maxlat, double maxlng, HttpServletRequest request) {
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

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		List<Entity> results =
				datastore.prepare(latlngQuery).asList(FetchOptions.Builder.withDefaults());

		JSONArray reportList = new JSONArray();

		boolean append = request.getAttribute(CustomHeader.LEVEL) != null;

		if(results.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			for(Entity report : results) {
				Map<String, Object> props = report.getProperties();
				if((double) props.get(DSUtils.REPORT_LNG) < maxlng
						&& (double) props.get(DSUtils.REPORT_LNG) > minlng) {
					JSONObject reportJson = new JSONObject();
					for(Entry<String, Object> prop : props.entrySet()) {
						if(prop.getKey().equals(DSUtils.REPORT_CREATIONTIME)) {
							String[] split = prop.getValue().toString().split(" ");
							String date = split[1] + "/" + split[2] + "/" + split[5];
							reportJson.put(prop.getKey(), date);
						} else
							reportJson.put(prop.getKey(), prop.getValue().toString());
					}
					if(append)
						appendVotes(reportJson, report);
					reportList.put(reportJson);
				}
			}

			return Response.ok().entity(reportList.toString()).build();
		}
	}

	@GET
	@Path("/getwithinradius")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsWithinRadius(
			@QueryParam(ParamName.LAT) double lat,
			@QueryParam(ParamName.LNG) double lng,
			@QueryParam(ParamName.RADIUS) double radius,
			@Context HttpServletRequest request) {
		if(lat == 0 || lng == 0 || radius == 0)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsWithinRadiusRetry(lat, lng, radius, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsWithinRadiusRetry(double lat, double lng,
			double radius, HttpServletRequest request) {
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

		List<Entity> results =	
				datastore.prepare(latlngQuery).asList(FetchOptions.Builder.withDefaults());

		JSONArray reportList = new JSONArray();

		boolean append = request.getAttribute(CustomHeader.LEVEL) != null;

		if(results.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			for(Entity report : results) {
				Map<String, Object> props = report.getProperties();
				if((double) props.get(DSUtils.REPORT_LNG) < bound.maxlng
						&& (double) props.get(DSUtils.REPORT_LNG) > bound.minlng) {
					JSONObject reportJson = new JSONObject();
					for(Entry<String, Object> prop : props.entrySet())
						reportJson.put(prop.getKey(), prop.getValue().toString());
					if(append)
						appendVotes(reportJson, report);
					reportList.put(reportJson);
				}
			}
			return Response.ok().entity(reportList.toString()).build();
		}
	}

	@GET
	@Path("/getinlocation")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReportsInLocation(@QueryParam(ParamName.LOCATION) String district,
			@Context HttpServletRequest request) {
		if(district == null)
			return Response.status(Status.EXPECTATION_FAILED).build();
		int retries = 5;
		while(true) {
			try {
				return getReportsInLocationRetry(district, request);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response getReportsInLocationRetry(String location, HttpServletRequest request) {
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

		Query latlngQuery = new Query(DSUtils.REPORT)
				.setFilter(allFilters);

		List<Entity> results =
				datastore.prepare(latlngQuery).asList(FetchOptions.Builder.withDefaults());

		JSONArray reportList = new JSONArray();

		boolean append = request.getAttribute(CustomHeader.LEVEL) != null;

		if(results.isEmpty())
			return Response.status(Status.NO_CONTENT).build();
		else {
			for(Entity report : results) {
				Map<String, Object> props = report.getProperties();
				JSONObject reportJson = new JSONObject();
				for(Entry<String, Object> prop : props.entrySet())
					reportJson.put(prop.getKey(), prop.getValue().toString());
				if(append)
					appendVotes(reportJson, report);
				reportList.put(reportJson);
			}

			return Response.ok().entity(reportList.toString()).build();
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
		Key reportKey = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Query votesQuery = new Query(DSUtils.REPORT_VOTES).setAncestor(reportKey);
		List<Entity> results = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());
		if(results.isEmpty()) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		Entity report = results.get(0);
		JSONObject object = new JSONObject();
		object.put(DSUtils.REPORT, report.getKey().getParent().getName());
		object.put(DSUtils.REPORT_VOTES_UP, report.getProperty(DSUtils.REPORT_VOTES_UP));
		object.put(DSUtils.REPORT_VOTES_DOWN, report.getProperty(DSUtils.REPORT_VOTES_DOWN));

		LOG.info(Message.VOTED_REPORT);
		return Response.ok(object).build();
	}

	private void appendVotes(JSONObject reportJson, Entity report) {
		Query votesQuery = new Query(DSUtils.REPORT_VOTES).setAncestor(report.getKey());
		List<Entity> results = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());

		Entity votes = results.get(0);
		reportJson.put(DSUtils.REPORT_VOTES_UP, votes.getProperty(DSUtils.REPORT_VOTES_UP));
		reportJson.put(DSUtils.REPORT_VOTES_DOWN, votes.getProperty(DSUtils.REPORT_VOTES_DOWN));
	}
}
