package com.wokesolutions.ignes.callbacks;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Transaction;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Log;
import com.wokesolutions.ignes.util.UserLevel;

import java.util.Date;
import java.util.logging.Logger;

public class LevelManager {

	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static Logger LOG = Logger.getLogger(LevelManager.class.getName());
	public final static int LEVEL2_POINTS = 2;
	public final static int LEVEL3_POINTS = 6;

	public static void changeLevel(Entity points, Entity reporter, Transaction txn) {
		LOG.info(Log.LEVEL_MANAGER_CHECKING);

		long pointsval = (long) points.getProperty(DSUtils.USERPOINTS_POINTS);

		String level = reporter.getProperty(DSUtils.USER_LEVEL).toString();

		if(!level.equals(UserLevel.LEVEL1) && !level.equals(UserLevel.LEVEL2)
				&& !level.equals(UserLevel.LEVEL3))
			return;

		if(pointsval < LEVEL2_POINTS && !level.equals(UserLevel.LEVEL1)) {
			Entity log = new Entity(DSUtils.LEVELLOG);
			log.setProperty(DSUtils.LEVELLOG_TIME, new Date());
			log.setProperty(DSUtils.LEVELLOG_OLDLEVEL, level);
			log.setProperty(DSUtils.LEVELLOG_NEWLEVEL, UserLevel.LEVEL1);
			log.setProperty(DSUtils.LEVELLOG_USER, reporter.getKey());

			reporter.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL1);

			datastore.put(txn, log);
			datastore.put(txn, reporter);
		} else if(pointsval >= LEVEL2_POINTS && !level.equals(UserLevel.LEVEL2)) {
			Entity log = new Entity(DSUtils.LEVELLOG);
			log.setProperty(DSUtils.LEVELLOG_TIME, new Date());
			log.setProperty(DSUtils.LEVELLOG_OLDLEVEL, level);
			log.setProperty(DSUtils.LEVELLOG_NEWLEVEL, UserLevel.LEVEL2);
			log.setProperty(DSUtils.LEVELLOG_USER, reporter.getKey());

			reporter.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL2);

			datastore.put(txn, log);
			datastore.put(txn, reporter);
		} else if(pointsval >= LEVEL3_POINTS && !level.equals(UserLevel.LEVEL3)) {
			Entity log = new Entity(DSUtils.LEVELLOG);
			log.setProperty(DSUtils.LEVELLOG_TIME, new Date());
			log.setProperty(DSUtils.LEVELLOG_OLDLEVEL, level);
			log.setProperty(DSUtils.LEVELLOG_NEWLEVEL, UserLevel.LEVEL3);
			log.setProperty(DSUtils.LEVELLOG_USER, reporter.getKey());

			reporter.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL3);

			datastore.put(txn, log);
			datastore.put(txn, reporter);
		}
	}
}
