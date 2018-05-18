package com.wokesolutions.ignes.api;

import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONObject;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;

@Path("/vote")
public class ReportVotes extends Report {
	
	private static final Logger LOG = Logger.getLogger(ReportVotes.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public ReportVotes() {}
	
	@POST
	@Path("/up/{report}")
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
		votes.setProperty(DSUtils.REPORTVOTES_UP, (long) votes.getProperty(DSUtils.REPORTVOTES_UP) + 1L); 

		datastore.put(votes);
		LOG.info(Message.VOTED_REPORT);
		return Response.ok().build();
	}

	@POST
	@Path("/down/{report}")
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
		votes.setProperty(DSUtils.REPORTVOTES_DOWN,
				(long) votes.getProperty(DSUtils.REPORTVOTES_DOWN) + 1L); 

		datastore.put(votes);
		LOG.info(Message.VOTED_REPORT);
		return Response.ok().build();
	}

	@GET
	@Path("/{report}")
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
		Query votesQuery = new Query(DSUtils.REPORT_VOTES)
				.setAncestor(KeyFactory.createKey(DSUtils.REPORT_VOTES, reportid));

		List<Entity> votes = datastore.prepare(votesQuery).asList(FetchOptions.Builder.withDefaults());

		String numUpvotes = "0";
		String numDownvotes = "0";

		if(!votes.isEmpty()) {
			numUpvotes = votes.get(0).getProperty(DSUtils.REPORTVOTES_UP).toString();
			numDownvotes = votes.get(0).getProperty(DSUtils.REPORTVOTES_DOWN).toString();
		} else
			throw new InternalServerErrorException();
		
		if(votes.isEmpty()) {
			LOG.info(Message.REPORT_NOT_FOUND);
			return Response.status(Status.NOT_FOUND).build();
		}

		JSONObject voteNums = new JSONObject()
				.put(DSUtils.REPORTVOTES_UP, numUpvotes)
				.put(DSUtils.REPORTVOTES_DOWN, numDownvotes);

		LOG.info(Message.VOTED_REPORT);
		return Response.ok(voteNums).build();
	}
}
