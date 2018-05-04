package com.wokesolutions.ignes.api;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;

@Path("/logout")
public class Logout {

	private static final Logger LOG = Logger.getLogger(Logout.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	@GET
	public Response logoutUser(@Context HttpServletRequest request,
			@Context HttpHeaders headers) {
		
		Transaction txn = datastore.beginTransaction();
		try {
			Algorithm algorithm = Algorithm.HMAC256(JWTUtils.SECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.build();

			String token = request.getHeader(JWTUtils.AUTHORIZATION);
			verifier.verify(token);
			
			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			LOG.info(Message.LOGGING_OUT + username);
			
			Key userKey = KeyFactory.createKey(DSUtils.USER, username);
			
			// Obtain the user login statistics
			Query statsQuery = new Query(DSUtils.USERSTATS).setAncestor(userKey);
			List<Entity> results = datastore.prepare(statsQuery).asList(FetchOptions.Builder.withDefaults());
			Entity stats = null;
			if (results.isEmpty()) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			} else {
				stats = results.get(0);
			}

			try {
				datastore.get(userKey);
				Entity log = new Entity(DSUtils.USERLOG, userKey);

				log.setProperty(DSUtils.USERLOG_TYPE, "out");
				log.setProperty(DSUtils.USERLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.USERLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.USERLOG_LATLON, headers.getHeaderString("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.USERLOG_CITY, headers.getHeaderString("X-AppEngine-City"));
				log.setProperty(DSUtils.USERLOG_COUNTRY, headers.getHeaderString("X-AppEngine-Country"));
				log.setProperty(DSUtils.USERLOG_TIME, new Date());

				stats.setProperty(DSUtils.USERSTATS_LOGOUTS, 1L + (long) stats.getProperty(DSUtils.USERSTATS_LOGOUTS));
				
				List<Entity> list = Arrays.asList(stats, log);
				datastore.put(txn, list);
				txn.commit();
				return Response.ok().build();
			} catch(EntityNotFoundException e) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
		} catch (UnsupportedEncodingException e){
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).build();
		} catch (JWTVerificationException e){
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).build();
		} finally {
			if(txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}

	@Path("/org")
	@GET
	public Response logoutOrg(@Context HttpServletRequest request,
			@Context HttpHeaders headers) {
		Transaction txn = datastore.beginTransaction();

		try {
			Algorithm algorithm = Algorithm.HMAC256(JWTUtils.SECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.ORG, true)
					.build();

			String token = request.getHeader(JWTUtils.AUTHORIZATION);
			verifier.verify(token);
			
			String nif = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			LOG.info(Message.LOGGING_OUT);
			
			Key orgKey = KeyFactory.createKey(DSUtils.ORG, nif);
			
			// Obtain the org login statistics
			Query statsQuery = new Query(DSUtils.ORGSTATS).setAncestor(orgKey);
			List<Entity> results = datastore.prepare(statsQuery).asList(FetchOptions.Builder.withDefaults());
			Entity stats = null;
			if (results.isEmpty()) {
				txn.rollback();
				return Response.status(Status.EXPECTATION_FAILED).build();
			} else {
				stats = results.get(0);
			}

			try {
				datastore.get(orgKey);
				Entity log = new Entity(DSUtils.ORGLOG, orgKey);

				log.setProperty(DSUtils.ORGLOG_TYPE, "out");
				log.setProperty(DSUtils.ORGLOG_IP, request.getRemoteAddr());
				log.setProperty(DSUtils.ORGLOG_HOST, request.getRemoteHost());
				log.setProperty(DSUtils.ORGLOG_LATLON, headers.getHeaderString("X-AppEngine-CityLatLong"));
				log.setProperty(DSUtils.ORGLOG_CITY, headers.getHeaderString("X-AppEngine-City"));
				log.setProperty(DSUtils.ORGLOG_COUNTRY, headers.getHeaderString("X-AppEngine-Country"));
				log.setProperty(DSUtils.ORGLOG_TIME, new Date());

				stats.setProperty("orgstats_logouts", 1L + (long) stats.getProperty("orgstats_logouts"));
				
				List<Entity> list = Arrays.asList(stats, log);
				datastore.put(txn, list);
				txn.commit();
				return Response.ok().build();
			} catch(EntityNotFoundException e) {
				return Response.status(Status.EXPECTATION_FAILED).build();
			}
			
		} catch (UnsupportedEncodingException e){
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).build();
		} catch (JWTVerificationException e){
			txn.rollback();
			return Response.status(Status.EXPECTATION_FAILED).build();
		} finally {
			if(txn.isActive()) {
				txn.rollback();
				LOG.info(Message.TXN_ACTIVE);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
	}
}
