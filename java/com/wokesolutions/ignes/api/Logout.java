package com.wokesolutions.ignes.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Log;

@Path("/logout")
public class Logout {

	private static final Logger LOG = Logger.getLogger(Logout.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final String OUT = "out";

	@POST
	@Path("/everywhere")
	public Response logoutUserEverywhere(@Context HttpHeaders headers,
			@Context HttpServletRequest request) {
		Object user = request.getAttribute(CustomHeader.USERNAME_ATT);

		if(user == null)
			return Response.status(Status.FORBIDDEN).build();

		int retries = 5;
		while(true) {
			try {
				return logoutUserEverywhereRetry(user.toString(), request, headers);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response logoutUserEverywhereRetry(String username,
			HttpServletRequest request, HttpHeaders headers) {

		Transaction txn = datastore.beginTransaction(TransactionOptions.Builder.withXG(true));

		Key userK = KeyFactory.createKey(DSUtils.USER, username);

		Filter tokenF = new Query.FilterPredicate(DSUtils.TOKEN_USER,
				FilterOperator.EQUAL, userK);
		Query tokenQ = new Query(DSUtils.TOKEN).setFilter(tokenF)
				.setKeysOnly();

		List<Entity> tokenlist = datastore.prepare(txn, tokenQ)
				.asList(FetchOptions.Builder.withDefaults());
		List<Key> tokens = new ArrayList<Key>(tokenlist.size());

		for(Entity token : tokenlist)
			tokens.add(token.getKey());

		datastore.delete(txn, tokens);

		txn.commit();

		return Response.ok().build();
	}

	@POST
	public Response logout(@Context HttpHeaders headers,
			@Context HttpServletRequest request) {

		Object user = request.getAttribute(CustomHeader.USERNAME_ATT);
		if(user == null)
			user = request.getAttribute(CustomHeader.NIF_ATT);
		if(user == null)
			return Response.status(Status.FORBIDDEN).build();

		String username = user.toString();

		boolean isOrg = false;

		try {
			datastore.get(KeyFactory.createKey(DSUtils.USER, username));
		} catch (EntityNotFoundException e1) {
			try {
				datastore.get(KeyFactory.createKey(DSUtils.ORG, username));
				isOrg = true;
			} catch (EntityNotFoundException e) {
				LOG.info(Log.USER_NOT_FOUND);
				return Response.status(Status.FORBIDDEN).build();
			}
		}

		int retries = 5;
		while(true) {
			try {
				return logoutUserRetry(username, request, isOrg);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	public Response logoutUserRetry(String username, HttpServletRequest request, boolean isOrg) {
		Transaction txn = datastore.beginTransaction
				(TransactionOptions.Builder.withXG(true));
		try {
			LOG.info(Log.LOGGING_OUT + username);

			Key userKey = KeyFactory.createKey(DSUtils.USER, username);

			Query statsQuery = new Query(DSUtils.USERSTATS).setAncestor(userKey);
			Entity stats;
			try {
				stats = datastore.prepare(statsQuery).asSingleEntity();
			} catch(TooManyResultsException e2) {
				LOG.info(Log.UNEXPECTED_ERROR);
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			if(stats == null) {
				txn.rollback();
				LOG.info(Log.UNEXPECTED_ERROR);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			try {
				datastore.get(txn, userKey);
			} catch(EntityNotFoundException e) {
				txn.rollback();
				LOG.info(Log.UNEXPECTED_ERROR);
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			Query query = new Query(DSUtils.TOKEN).setKeysOnly();

			String token = request.getHeader(CustomHeader.AUTHORIZATION);

			LOG.info(token);

			Filter stringF = new Query
					.FilterPredicate(DSUtils.TOKEN_STRING, FilterOperator.EQUAL, token);

			query.setFilter(stringF);

			Entity tokenE;
			try {
				tokenE = datastore.prepare(query).asSingleEntity();
			} catch(TooManyResultsException e2) {
				LOG.info(Log.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			datastore.delete(txn, tokenE.getKey());

			String deviceid = request.getAttribute(CustomHeader.DEVICE_ID_ATT).toString();

			Entity log = new Entity(DSUtils.USERLOG, userKey);

			log.setProperty(DSUtils.USERLOG_TYPE, OUT);
			log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
			log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
			log.setProperty(DSUtils.USERLOG_LATLON, request.getHeader("X-AppEngine-CityLatLong"));
			log.setProperty(DSUtils.USERLOG_CITY, request.getHeader("X-AppEngine-City"));
			log.setProperty(DSUtils.USERLOG_COUNTRY, request.getHeader("X-AppEngine-Country"));
			log.setProperty(DSUtils.USERLOG_TIME, new Date());
			log.setProperty(DSUtils.USERLOG_DEVICE, deviceid);

			stats.setProperty(DSUtils.USERSTATS_LOGOUTS,
					1L + (long) stats.getProperty(DSUtils.USERSTATS_LOGOUTS));

			List<Entity> list = Arrays.asList(stats, log);
			datastore.put(txn, list);

			txn.commit();
			return Response.ok().build();
		} finally {
			if(txn.isActive()) {
				txn.rollback();
				LOG.info(Log.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
}
