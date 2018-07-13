package com.wokesolutions.ignes.util;

import java.util.Arrays;
import java.util.Date;
import java.util.logging.Logger;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.wokesolutions.ignes.api.Report;
import com.wokesolutions.ignes.callbacks.LevelManager;
import com.wokesolutions.ignes.exceptions.VoteException;

public class ReportVotes {

	public static final String NEUTRAL = "neutral";
	public static final String UP = "up";
	public static final String DOWN = "down";
	public static final String SPAM = "spam";

	private static final long TO_DOWN_POINTS_REL = 6;
	private static final long TO_DOWN_POINTS_UPS = 3;

	private static final long POINTS_UP = 3;
	private static final long POINTS_DOWN = -1;
	private static final long POINTS_SPAM = -4;

	private static final Logger LOG = Logger.getLogger(Report.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public static void vote(String vote, String reportid, String username)
			throws VoteException {
		Key userK = KeyFactory.createKey(DSUtils.USER, username);
		Entity user;

		try {
			user = datastore.get(userK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			throw new VoteException();
		}

		Key reportK = KeyFactory.createKey(DSUtils.REPORT, reportid);
		Entity report;

		try {
			report = datastore.get(reportK);
		} catch(EntityNotFoundException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			throw new VoteException();
		}

		boolean hasvote;

		Entity uservote;
		Filter uservoteFRep = new Query.FilterPredicate(DSUtils.USERVOTE_REPORT,
				FilterOperator.EQUAL, report.getKey());
		Filter uservoteFUser = new Query.FilterPredicate(DSUtils.USERVOTE_USER,
				FilterOperator.EQUAL, user.getKey());
		CompositeFilter filter = new Query.CompositeFilter
				(CompositeFilterOperator.AND, Arrays.asList(uservoteFRep, uservoteFUser));
		Query uservoteQ = new Query(DSUtils.USERVOTE).setFilter(filter);

		try {
			uservote = datastore.prepare(uservoteQ).asSingleEntity();

			if(uservote == null)
				hasvote = false;
			else
				hasvote = true;
		} catch(TooManyResultsException e) {
			LOG.info(Log.UNEXPECTED_ERROR);
			throw new VoteException();
		}

		Key reporterK = (Key) report.getProperty(DSUtils.REPORT_USER);
		Entity reporter;
		try {
			reporter = datastore.get(reporterK);
		} catch(EntityNotFoundException e) {
			throw new VoteException();
		}

		Query pointsQ = new Query(DSUtils.USERPOINTS).setAncestor(reporterK);
		Entity points;

		try {
			points = datastore.prepare(pointsQ).asSingleEntity();

			if(points == null)
				throw new VoteException();
		} catch(TooManyResultsException e) {
			throw new VoteException();
		}

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		Entity reportvotes;
		Query reportvotesQ = new Query(DSUtils.REPORTVOTES).setAncestor(reportK);

		try {
			reportvotes = datastore.prepare(reportvotesQ).asSingleEntity();

			if(reportvotes == null)
				throw new VoteException();
		} catch(TooManyResultsException e) {
			throw new VoteException();
		}

		try {
			if(vote.equals(SPAM)) {
				spam(points, reportvotes, user, report, txn);
				return;
			}

			if(hasvote)
				if(vote.equals(NEUTRAL))
					removeVote(points, reportvotes, uservote, txn);
				else
					changeVote(vote, uservote, points, reportvotes, txn);
			else
				newVote(user, points, report, vote, reportvotes, txn);

			LevelManager.changeLevel(points, reporter, txn);
			
			txn.commit();
		} catch(VoteException e) {
			txn.rollback();
			throw e;
		} finally {
			if(txn.isActive()) {
				LOG.info(Log.TXN_ACTIVE);
				txn.rollback();
			}
		}
	}

	public static void newVote(Entity user, Entity points, Entity report,
			String vote, Entity reportvotes, Transaction txn) {

		Entity newvote = new Entity(DSUtils.USERVOTE);
		newvote.setProperty(DSUtils.USERVOTE_USER, user.getKey());
		newvote.setProperty(DSUtils.USERVOTE_REPORT, report.getKey());
		newvote.setProperty(DSUtils.USERVOTE_TYPE, vote);
		newvote.setProperty(DSUtils.USERVOTE_TIME, new Date());

		datastore.put(txn, newvote);

		long pointsval = (long) points.getProperty(DSUtils.USERPOINTS_POINTS);

		long relevance = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_RELEVANCE);

		if(vote.equals(UP)) {
			long ups = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_UP);

			reportvotes.setProperty(DSUtils.REPORTVOTES_UP, ups + 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE, relevance + POINTS_UP);

			points.setProperty(DSUtils.USERPOINTS_POINTS, pointsval + POINTS_UP);
		} else if(vote.equals(DOWN)) {
			long downs = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_DOWN);

			reportvotes.setProperty(DSUtils.REPORTVOTES_UP, downs + 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE, relevance + POINTS_DOWN);

			points.setProperty(DSUtils.USERPOINTS_POINTS, pointsval + POINTS_DOWN);
		}

		datastore.put(txn, reportvotes);
		datastore.put(txn, points);
	}

	public static void spam(Entity points, Entity reportvotes, Entity user,
			Entity report, Transaction txn) throws VoteException {

		long pointsval = (long) points.getProperty(DSUtils.USERPOINTS_POINTS);

		long relevance = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_RELEVANCE);
		long spams = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_SPAM);

		points.setProperty(DSUtils.USERPOINTS_POINTS, pointsval + POINTS_SPAM);

		reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE, relevance + POINTS_SPAM);
		reportvotes.setProperty(DSUtils.REPORTVOTES_SPAM, spams + 1);

		Entity newvote = new Entity(DSUtils.USERVOTE);
		newvote.setProperty(DSUtils.USERVOTE_USER, user.getKey());
		newvote.setProperty(DSUtils.USERVOTE_TYPE, SPAM);
		newvote.setProperty(DSUtils.USERVOTE_REPORT, report.getKey());
		newvote.setProperty(DSUtils.USERVOTE_TIME, new Date());

		datastore.put(txn, points);
		datastore.put(txn, reportvotes);
		datastore.put(txn, newvote);
	}

	public static void removeVote(Entity points, Entity reportvotes,
			Entity oldvote, Transaction txn) throws VoteException {
		Object voteO = oldvote.getProperty(DSUtils.USERVOTE_TYPE);

		if(voteO == null)
			throw new VoteException();

		String vote = voteO.toString();

		long pointsval = (long) points.getProperty(DSUtils.USERPOINTS_POINTS);

		long relevance = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_RELEVANCE);

		if(vote.equals(UP)) {
			long ups = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_UP);

			points.setProperty(DSUtils.USERPOINTS_POINTS, pointsval - POINTS_UP);

			if(ups > 0)
				reportvotes.setProperty(DSUtils.REPORTVOTES_UP, ups - 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE,
					relevance - POINTS_UP);
		} else if(vote.equals(DOWN)) {
			long downs = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_DOWN);

			points.setProperty(DSUtils.USERPOINTS_POINTS, pointsval - POINTS_DOWN);

			if(downs > 0)
				reportvotes.setProperty(DSUtils.REPORTVOTES_UP, downs - 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE,
					relevance - POINTS_DOWN);
		} else
			throw new VoteException();

		datastore.delete(txn, oldvote.getKey());
		datastore.put(txn, points);
		datastore.put(txn, reportvotes);
	}

	public static void changeVote(String vote, Entity oldvote, Entity points,
			Entity reportvotes, Transaction txn) throws VoteException {
		String oldvoteS = oldvote.getProperty(DSUtils.USERVOTE_TYPE).toString();

		long pointsval = (long) points.getProperty(DSUtils.USERPOINTS_POINTS);

		long ups = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_UP);
		long downs = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_DOWN);
		long relevance = (long) reportvotes.getProperty(DSUtils.REPORTVOTES_RELEVANCE);

		oldvote.setProperty(DSUtils.USERVOTE_TIME, new Date());

		datastore.put(txn, oldvote);

		if(vote.equals(UP) && oldvoteS.equals(DOWN)) {
			points.setProperty(DSUtils.USERPOINTS_POINTS, pointsval +  POINTS_UP - POINTS_DOWN);

			datastore.put(txn, points);

			reportvotes.setProperty(DSUtils.REPORTVOTES_UP, ups + 1);
			if(downs > 0)
				reportvotes.setProperty(DSUtils.REPORTVOTES_DOWN, downs - 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE,
					relevance + POINTS_UP - POINTS_DOWN);
		} else if(vote.equals(DOWN) && oldvoteS.equals(UP)) {
			if(ups > TO_DOWN_POINTS_UPS && relevance < TO_DOWN_POINTS_REL) {
				points.setProperty(DSUtils.USERPOINTS_POINTS,
						pointsval -  POINTS_UP + POINTS_DOWN);

				datastore.put(txn, points);
			}

			if(ups > 0)
				reportvotes.setProperty(DSUtils.REPORTVOTES_UP, ups - 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_DOWN, downs + 1);
			reportvotes.setProperty(DSUtils.REPORTVOTES_RELEVANCE,
					relevance - POINTS_UP + POINTS_DOWN);
		} else
			return;

		datastore.put(txn, reportvotes);
	}
}
