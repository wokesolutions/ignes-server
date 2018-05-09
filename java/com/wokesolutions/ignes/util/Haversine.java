package com.wokesolutions.ignes.util;

public class Haversine {
	
    private static final int EARTH_RADIUS = 6371;
    private static final double EARTH_PER = Math.PI * EARTH_RADIUS * EARTH_RADIUS;
    
    public static double distance(double startLat, double startLong,
                                  double endLat, double endLong) {

        double dLat  = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat   = Math.toRadians(endLat);

        double a = haversin(dLat) + Math.cos(startLat) * Math.cos(endLat) * haversin(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    public static double haversin(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }
    
    public static Boundaries getBoundaries(double lat, double lng, double radius) {
    	double degrees = radius * (360 / EARTH_PER);
    	return new Boundaries(lat + degrees, lat - degrees, lng + degrees, lng - degrees);
    }
}