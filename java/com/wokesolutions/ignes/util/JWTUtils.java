package com.wokesolutions.ignes.util;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;

public class JWTUtils {

	public final static String ISSUER = "Ignes";

	// Sent in the token
	public static final String USERNAME = "usr";
	public static final String IAT = "iat";
	public static final String LEVEL = "lvl";
	
	// Not needed yet
	public static final String EMAIL = "eml";
	public static final String BIRTH = "bth";
	public static final String PHONE = "phn";
	
	public static String createJWT(String username, String level, Date date)
			throws IllegalArgumentException, UnsupportedEncodingException {
		Algorithm algorithm = Algorithm.HMAC256(Secrets.JWTSECRET);
		JWTCreator.Builder token = JWT.create()
				.withIssuer(ISSUER)
				.withClaim(USERNAME, username)
				.withClaim(IAT, date);
		
		return token.sign(algorithm);
	}
}
