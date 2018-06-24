package com.wokesolutions.ignes.api;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONObject;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.data.VotesData;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.ParamName;

@Path("/vote")
public class ReportVotes extends Report {

	private static final Logger LOG = Logger.getLogger(ReportVotes.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final String VOTE = "vote";
	private static final String UP = "up";
	private static final String DOWN = "down";
	private static final String SPAM = "spam";

	public ReportVotes() {}

	@POST
	@Path("/up/{report}")
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
	@Path("/down/{report}")
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
	@Path("/spam/{report}")
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
	@Path("/multiple")
	@Consumes(CustomHeader.JSON_CHARSET_UTF8)
	public Response voteAll(VotesData votes, @Context HttpServletRequest request) {
		int retries = 5;
		
		LOG.info(votes.toString());

		String username = request.getAttribute(CustomHeader.USERNAME_ATT).toString();

		while(true) {
			try {
				for(int i = 1; i <= 10; i++) {
					String repVote = votes.getReport(i);
					
					if(repVote == null || repVote.equals(""))
						break;

					String[] split = repVote.split(" ");
					String reportid = split[0];
					String vote = split[1];

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
}
