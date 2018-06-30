package com.wokesolutions.ignes.callbacks;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PostPut;
import com.google.appengine.api.datastore.PutContext;
import com.google.appengine.api.datastore.Transaction;
import com.wokesolutions.ignes.api.Profile;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;
import com.wokesolutions.ignes.util.UserLevel;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class LevelManager {

	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	private static Logger LOG = Logger.getLogger(LevelManager.class.getName());
	public final static int LEVEL2_POINTS = 2;
	public final static int LEVEL3_POINTS = 6;

	@PostPut(kinds = {DSUtils.USERPOINTS})
	void changeLevel(PutContext context) {
		Transaction txn = datastore.beginTransaction();

		try {
			Entity points = context.getCurrentElement();
			Key userKey = points.getParent();
			
			LOG.info(Message.LEVEL_MANAGER_CHECKING + userKey.getName());

			Entity user;
			try {
				user = datastore.get(points.getParent());
			} catch (EntityNotFoundException e) {
				LOG.info(Message.UNEXPECTED_ERROR);
				return;
			}
			
			String level = user.getProperty(DSUtils.USER_LEVEL).toString();
			
			if(!level.equals(UserLevel.LEVEL1) && !level.equals(UserLevel.LEVEL2)
					&& !level.equals(UserLevel.LEVEL3))
				return;
			
			if(!user.getProperty(DSUtils.USER_ACTIVATION).toString().equals(Profile.ACTIVATED)) {
				LOG.info(Message.USER_NOT_ACTIVE);
				return;
			}

			String oldLevel = user.getProperty(DSUtils.USER_LEVEL).toString();
			String newLevel;

			if(oldLevel.equals(UserLevel.LEVEL1)) {
				if(Integer.parseInt(points.getProperty(DSUtils.USERPOINTS_POINTS).toString()) > LEVEL2_POINTS) {
					LOG.info(Message.TRYING_TO_CHANGE_LEVEL2 + userKey.getName());
					user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL2);
					newLevel = UserLevel.LEVEL2;
				} else
					return;
			} else if(oldLevel.equals(UserLevel.LEVEL2)) {
				if(Integer.parseInt(points.getProperty(DSUtils.USERPOINTS_POINTS).toString()) <= LEVEL2_POINTS) {
					LOG.info(Message.TRYING_TO_CHANGE_LEVEL1 + userKey.getName());
					user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL1);
					newLevel = UserLevel.LEVEL1;
				} else if(Integer.parseInt(points.getProperty(DSUtils.USERPOINTS_POINTS).toString())
						> LEVEL3_POINTS) {
					LOG.info(Message.TRYING_TO_CHANGE_LEVEL3 + userKey.getName());
					user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL3);
					newLevel = UserLevel.LEVEL3;
				} else
					return;
			} else if(oldLevel.equals(UserLevel.LEVEL3)) {
				if(Integer.parseInt(points.getProperty(DSUtils.USERPOINTS_POINTS).toString()) <= LEVEL3_POINTS) {
					LOG.info(Message.TRYING_TO_CHANGE_LEVEL2 + userKey.getName());
					user.setProperty(DSUtils.USER_LEVEL, UserLevel.LEVEL2);
					newLevel = UserLevel.LEVEL2;
				} else
					return;
			} else {
				return;
			}

			Entity levellog = new Entity(DSUtils.LEVELLOG, userKey);
			levellog.setProperty(DSUtils.LEVELLOG_OLDLEVEL, oldLevel);
			levellog.setProperty(DSUtils.LEVELLOG_NEWLEVEL, newLevel);
			levellog.setProperty(DSUtils.LEVELLOG_DATE, new Date());

			List<Entity> ents = Arrays.asList(levellog, user);
			datastore.put(txn, ents);
			txn.commit();
		} finally {
			if(txn.isActive()) {
				LOG.info(Message.TXN_ACTIVE);
				txn.rollback();
				return;
			}
		}

	}
}