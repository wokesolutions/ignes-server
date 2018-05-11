package com.wokesolutions.ignes.api;

import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;
import com.wokesolutions.ignes.util.Message;

@Path("/orgcode")
public class OrgCode {

	private static final Logger LOG = Logger.getLogger(Register.class.getName());
	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public OrgCode() {}

	@Path("/generate")
	@GET
	@Produces(MediaType.APPLICATION_JSON + CustomHeader.CHARSET_UTF8)
	public Response generateCode(@Context HttpServletRequest request) {

		String org = request.getAttribute(CustomHeader.NIF).toString();
		LOG.info(Message.GENERATING_CODE + org);

		Key orgKey = KeyFactory.createKey(DSUtils.ORG, org);
		Entity orgEnt;
		try {
			orgEnt = datastore.get(orgKey);
			String[] orgName = orgEnt.getProperty(DSUtils.ORG_NAME).toString().split(" ");
			String initials = "";
			for(String name : orgName)
				initials += name.toLowerCase().charAt(0);

			LOG.info(Message.CODE_INITIALS + initials);

			String codeStr = initials + String.valueOf(System.currentTimeMillis()).substring(7 - initials.length());

			Query codeQuery = new Query(DSUtils.ORGCODE).setAncestor(orgKey);
			Filter active =
					new Query.FilterPredicate(DSUtils.ORGCODE_ACTIVE, FilterOperator.EQUAL, true);
			codeQuery.setFilter(active);
			List<Entity> results = datastore.prepare(codeQuery).asList(FetchOptions.Builder.withDefaults());

			if(!results.isEmpty()) {
				String code = results.get(0).getProperty(DSUtils.ORGCODE_CODE).toString();
				return Response.status(Status.CONFLICT).entity(Message.ORG_CODE_ALREADY_EXISTS)
						.entity(code).build();
			} else {
				Entity orgCode = new Entity(DSUtils.ORGCODE, orgKey);
				LOG.info(codeStr);
				orgCode.setProperty(DSUtils.ORGCODE_CODE, codeStr);
				orgCode.setProperty(DSUtils.ORGCODE_ACTIVE, true);
				datastore.put(orgCode);
				
				return Response.ok().entity(codeStr).build();
			}
		} catch (EntityNotFoundException e) {
			return Response.status(Status.EXPECTATION_FAILED).entity(Message.ORG_NOT_FOUND).build();
		}
	}
}