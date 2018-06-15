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
import com.google.cloud.datastore.DatastoreException;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;

@Path("/logout")
public class Logout {

	private static final Logger LOG = Logger.getLogger(Logout.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	private static final String OUT = "out";

	@POST
	@Path("/everywhere")
	public Response logoutUserEverywhere(@Context HttpHeaders headers,
			@Context HttpServletRequest request) {
		String username = request.getAttribute(CustomHeader.USERNAME).toString();

		int retries = 5;
		while(true) {
			try {
				return logoutUserEverywhereRetry(username, request, headers);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	private Response logoutUserEverywhereRetry(String username,
			HttpServletRequest request, HttpHeaders headers) {
		Query query = new Query(DSUtils.TOKEN)
				.setAncestor(KeyFactory.createKey(DSUtils.USER, username))
				.setKeysOnly();

		List<Entity> tokens = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
		
		if(tokens.isEmpty()) {
			LOG.info(Message.NO_TOKENS_FOUND);
			return Response.status(Status.EXPECTATION_FAILED).build();
		}
		
		List<Key> keys = new ArrayList<Key>(tokens.size());
		
		for(Entity token : tokens)
			keys.add(token.getKey());
		
		datastore.delete(keys);
		
		return Response.ok().build();
	}

	@POST
	public Response logoutUser(@Context HttpHeaders headers,
			@Context HttpServletRequest request) {

		String username = request.getAttribute(CustomHeader.USERNAME).toString();

		int retries = 5;
		while(true) {
			try {
				return logoutUserRetry(username, request, headers);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	public Response logoutUserRetry(String username, HttpServletRequest request, HttpHeaders headers) {

		Transaction txn = datastore.beginTransaction();
		try {
			LOG.info(Message.LOGGING_OUT + username);

			Key userKey = KeyFactory.createKey(DSUtils.USER, username);

			// Obtain the user login statistics
			Query statsQuery = new Query(DSUtils.USERSTATS).setAncestor(userKey);
			Entity stats;
			try {
				stats = datastore.prepare(statsQuery).asSingleEntity();
			} catch(TooManyResultsException e2) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			try {
				datastore.get(userKey);
				Entity log = new Entity(DSUtils.USERLOG, userKey);

				log.setProperty(DSUtils.USERLOG_TYPE, OUT);
				log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.USERLOG_LATLON, headers.getHeaderString("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.USERLOG_CITY, headers.getHeaderString("X-AppEngine-City"));
				log.setProperty(DSUtils.USERLOG_COUNTRY, headers.getHeaderString("X-AppEngine-Country"));
				log.setProperty(DSUtils.USERLOG_TIME, new Date());

				stats.setProperty(DSUtils.USERSTATS_LOGOUTS,
						1 + (long) stats.getProperty(DSUtils.USERSTATS_LOGOUTS));

				List<Entity> list = Arrays.asList(stats, log);
				datastore.put(txn, list);

				Query query = new Query(DSUtils.TOKEN).setAncestor(userKey).setKeysOnly();

				Filter filter = new Query
						.FilterPredicate(DSUtils.TOKEN_STRING, FilterOperator.EQUAL,
								request.getHeader(JWTUtils.AUTHORIZATION));

				query.setFilter(filter);

				Entity token;
				try {
					token = datastore.prepare(txn, query).asSingleEntity();
				} catch(TooManyResultsException e2) {
					LOG.info(Message.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
				
				datastore.delete(txn, token.getKey());

				txn.commit();
				return Response.ok().build();
			} catch(EntityNotFoundException e) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
		} finally {
			if(txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@Path("/org")
	@POST
	public Response logoutOrg(@Context HttpHeaders headers,
			@Context HttpServletRequest request) {

		String nif = request.getAttribute(CustomHeader.NIF).toString();

		int retries = 5;
		while(true) {
			try {
				return logoutOrgRetry(nif, request, headers);
			} catch(DatastoreException e) {
				if(retries == 0)
					return Response.status(Status.REQUEST_TIMEOUT).build();
				retries--;
			}
		}
	}

	public Response logoutOrgRetry(String nif, HttpServletRequest request, HttpHeaders headers) {
		Transaction txn = datastore.beginTransaction();

		try {
			LOG.info(Message.LOGGING_OUT);

			Key orgKey = KeyFactory.createKey(DSUtils.ORG, nif);

			// Obtain the org login statistics
			Query statsQuery = new Query(DSUtils.ORGSTATS).setAncestor(orgKey);
			Entity stats;
			try {
				stats = datastore.prepare(statsQuery).asSingleEntity();
			} catch(TooManyResultsException e2) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

			try {
				datastore.get(orgKey);
				Entity log = new Entity(DSUtils.ORGLOG, orgKey);

				log.setProperty(DSUtils.ORGLOG_TYPE, OUT);
				log.setProperty(DSUtils.ORGLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.ORGLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.ORGLOG_LATLON, headers.getHeaderString("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.ORGLOG_CITY, headers.getHeaderString("X-AppEngine-City"));
				log.setProperty(DSUtils.ORGLOG_COUNTRY, headers.getHeaderString("X-AppEngine-Country"));
				log.setProperty(DSUtils.ORGLOG_TIME, new Date());

				stats.setProperty(DSUtils.ORGSTATS_LOGOUTS,
						1 + (long) stats.getProperty(DSUtils.ORGSTATS_LOGOUTS));

				List<Entity> list = Arrays.asList(stats, log);
				datastore.put(txn, list);

				Query query = new Query(DSUtils.TOKEN).setAncestor(orgKey).setKeysOnly();

				Filter filter = new Query
						.FilterPredicate(DSUtils.TOKEN_STRING, FilterOperator.EQUAL,
								request.getHeader(JWTUtils.AUTHORIZATION));

				query.setFilter(filter);

				Entity token;
				try {
					token = datastore.prepare(txn, query).asSingleEntity();
				} catch(TooManyResultsException e2) {
					LOG.info(Message.UNEXPECTED_ERROR);
					return Response.status(Status.INTERNAL_SERVER_ERROR).build();
				}
				
				datastore.delete(txn, token.getKey());
				txn.commit();
				return Response.ok().build();
			} catch(EntityNotFoundException e) {
				return Response.status(Status.EXPECTATION_FAILED).build();
			}

		} finally {
			if(txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
}
