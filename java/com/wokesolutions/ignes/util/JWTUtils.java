package com.wokesolutions.ignes.util;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

public class JWTUtils {

	public final static String ISSUER = "Ignes";

	// Sent in the token
	public static final String USERNAME = "usr";
	public static final String ADDRESS = "adr";
	public static final String ADMIN = "adm";
	public static final String LEVEL1 = "lv1";
	public static final String LEVEL2 = "lv2";
	public static final String LEVEL3 = "lv3";
	public static final String WORKER = "wrk";
	public static final String ORG = "org";
	public static final String IAT = "iat";
	
	// Not needed yet
	public static final String EMAIL = "eml";
	public static final String BIRTH = "bth";
	public static final String PHONE = "phn";
	
	public static String createJWT(String username, String level, Date date)
			throws IllegalArgumentException, UnsupportedEncodingException {
		Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
		String token = null;
		
		if(level.equals(UserLevel.ADMIN)) {
			token = JWT.create()
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.ADMIN, UserLevel.ADMIN)
					.withClaim(JWTUtils.WORKER, UserLevel.WORKER)
					.withClaim(JWTUtils.LEVEL3, UserLevel.LEVEL3)
					.withClaim(JWTUtils.LEVEL2, UserLevel.LEVEL2)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.withClaim(JWTUtils.USERNAME, username)
					.withClaim(JWTUtils.IAT, date)
					.sign(algorithm);
		} else if(level.equals(UserLevel.WORKER)) { //TODO CAREFUL WITH PERMISSION
			token = JWT.create()
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.WORKER, UserLevel.WORKER)
					.withClaim(JWTUtils.LEVEL3, UserLevel.LEVEL3)
					.withClaim(JWTUtils.LEVEL2, UserLevel.LEVEL2)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.withClaim(JWTUtils.USERNAME, username)
					.withClaim(JWTUtils.IAT, date)
					.sign(algorithm);
		} else if(level.equals(UserLevel.LEVEL3)) {
			token = JWT.create()
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL3, UserLevel.LEVEL3)
					.withClaim(JWTUtils.LEVEL2, UserLevel.LEVEL2)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.withClaim(JWTUtils.USERNAME, username)
					.withClaim(JWTUtils.IAT, date)
					.sign(algorithm);
		} else if(level.equals(UserLevel.LEVEL2)) {
			token = JWT.create()
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL2, UserLevel.LEVEL2)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.withClaim(JWTUtils.USERNAME, username)
					.withClaim(JWTUtils.IAT, date)
					.sign(algorithm);
		} else if(level.equals(UserLevel.LEVEL1)) {
			token = JWT.create()
					.withIssuer(JWTUtils.ISSUER)
					.withClaim(JWTUtils.LEVEL1, UserLevel.LEVEL1)
					.withClaim(JWTUtils.USERNAME, username)
					.withClaim(JWTUtils.IAT, date)
					.sign(algorithm);
		}
		
		return token;
	}
}
