package com.wokesolutions.ignes.api;

import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.JWTUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.Secrets;
import com.wokesolutions.ignes.util.UserLevel;

@Path("/verifytoken")
public class VerifyToken {

	public static final Logger LOG = Logger.getLogger(VerifyToken.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public VerifyToken() {}

	@GET
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyToken(@Context HttpHeaders headers) {
		try {
			LOG.info(Message.VERIFYING_TOKEN);

			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			LOG.info(Message.VERIFYING_TOKEN_OF_USER + username);

			Entity user;
			try {
				user = datastore.get(KeyFactory.createKey(DSUtils.USER, username));
			} catch (EntityNotFoundException e) {
				try {
					user = datastore.get(KeyFactory.createKey(DSUtils.ORG, username));
				} catch (EntityNotFoundException e1) {
					LOG.info(Message.INVALID_TOKEN);
					return Response.status(Status.FORBIDDEN).build();
				}
			}

			Entity tokenE;
			try {
				Query query = new Query(DSUtils.TOKEN).setAncestor(KeyFactory.createKey(DSUtils.USER, username));
				Filter filter = new Query.FilterPredicate(DSUtils.TOKEN_STRING, FilterOperator.EQUAL, token);
				query.setFilter(filter);

				tokenE = datastore.prepare(query).asSingleEntity();
				if(tokenE == null) {
					Query query2 = new Query(DSUtils.TOKEN).setAncestor(KeyFactory.createKey(DSUtils.ORG, username));
					query2.setFilter(filter);

					tokenE = datastore.prepare(query2).asSingleEntity();

					if(tokenE == null) {
						LOG.info(Message.INVALID_TOKEN);
						return Response.status(Status.FORBIDDEN).build();
					}
				}
			} catch(TooManyResultsException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return Response.status(Status.INTERNAL_SERVER_ERROR).build();
			}

			return Response.ok()
					.header(CustomHeader.LEVEL, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();
		} catch (UnsupportedEncodingException e){
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
		} catch (JWTVerificationException e){
			return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
		}
	}

	@GET
	@Path("/user")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyTokenUser(@Context HttpHeaders headers) {
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			try {
				datastore.get(KeyFactory.createKey(DSUtils.USER, username));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.INVALID_TOKEN);
				return Response.status(Status.FORBIDDEN).build();
			}

			return Response.ok()
					//.header(CustomHeader.LEVEL_ATT, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();
		} catch (UnsupportedEncodingException e){
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
		} catch (JWTVerificationException e){
			return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
		}
	}

	@GET
	@Path("/user2")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyTokenUser2(@Context HttpHeaders headers) {
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL2, UserLevel.LEVEL2)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			try {
				datastore.get(KeyFactory.createKey(DSUtils.USER, username));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.INVALID_TOKEN);
				return Response.status(Status.FORBIDDEN).build();
			}

			return Response.ok()
					//.header(CustomHeader.LEVEL_ATT, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();
		} catch (UnsupportedEncodingException e){
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
		} catch (JWTVerificationException e){
			return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
		}
	}

	@GET
	@Path("/user3")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyTokenUser3(@Context HttpHeaders headers) {
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL3, UserLevel.LEVEL3)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			try {
				datastore.get(KeyFactory.createKey(DSUtils.USER, username));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.INVALID_TOKEN);
				return Response.status(Status.FORBIDDEN).build();
			}

			return Response.ok()
					//.header(CustomHeader.LEVEL_ATT, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();		} catch (UnsupportedEncodingException e){
						return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
					} catch (JWTVerificationException e){
						return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
					}
	}

	@GET
	@Path("/admin")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyTokenAdmin(@Context HttpHeaders headers) {
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.ADMIN, UserLevel.ADMIN)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			String username = JWT.decode(token).getClaim(JWTUtils.USERNAME).asString();

			try {
				datastore.get(KeyFactory.createKey(DSUtils.USER, username));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.INVALID_TOKEN);
				return Response.status(Status.FORBIDDEN).build();
			}

			return Response.ok()
					//.header(CustomHeader.LEVEL_ATT, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();
		} catch (UnsupportedEncodingException e){
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
		} catch (JWTVerificationException e){
			return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
		}
	}

	@GET
	@Path("/worker")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyTokenWorker(@Context HttpHeaders headers) {
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.WORKER, UserLevel.WORKER)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			String username = JWT.decode(token).getClaim(JWTUtils.EMAIL).asString();

			try {
				datastore.get(KeyFactory.createKey(DSUtils.WORKER, username));
			} catch (EntityNotFoundException e) {
				LOG.info(Message.INVALID_TOKEN);
				return Response.status(Status.FORBIDDEN).build();
			}

			return Response.ok()
					//.header(CustomHeader.LEVEL_ATT, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();
		} catch (UnsupportedEncodingException e){
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
		} catch (JWTVerificationException e){
			return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
		}
	}

	@GET
	@Path("/org")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response verifyTokenOrg(@Context HttpHeaders headers) {
		try {
			Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.ORG, true)
					.build();

			String token = headers.getHeaderString(CustomHeader.AUTHORIZATION);
			verifier.verify(token);

			return Response.ok()
					//.header(CustomHeader.LEVEL_ATT, user.getProperty(DSUtils.USER_LEVEL))
					.header(CustomHeader.ACTIVATED, CustomHeader.TRUE)
					.entity(Message.OK).build();
		} catch (UnsupportedEncodingException e){
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.BAD_FORMAT).build();
		} catch (JWTVerificationException e){
			return Response.status(Status.FORBIDDEN).entity(Message.INVALID_TOKEN).build();
		}
	}
}