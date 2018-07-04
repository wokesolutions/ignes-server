package com.wokesolutions.ignes.api;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.wokesolutions.ignes.util.CustomHeader;
import com.wokesolutions.ignes.util.DSUtils;

@Path("/category")
public class Category {

	private static final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
	
	public static final int TO_LIST = 50;
	
	private static final int NUM_DEF = 10;
	
	public static final String LIXO = "Limpeza de lixo geral";
	public static final String PESADOS = "Transportes pesados";
	public static final String PERIGOSOS = "Transportes perigosos";
	public static final String PESSOAS = "Transporte de pessoas";
	public static final String TRANSPORTE = "Transportes gerais";
	public static final String MADEIRAS = "Madeiras";
	public static final String CARCACAS = "Carcaças";
	public static final String BIOLOGICO = "Outros resíduos biológicos";
	public static final String JARDINAGEM = "Jardinagem";
	public static final String MATAS = "Limpeza de matas/florestas";
	
	@GET
	@Path("/list")
	@Produces(CustomHeader.JSON_CHARSET_UTF8)
	public Response list() {
		
		JSONArray array = new JSONArray();
		array.put(LIXO);
		array.put(PESADOS);
		array.put(PERIGOSOS);
		array.put(PESSOAS);
		array.put(TRANSPORTE);
		array.put(MADEIRAS);
		array.put(CARCACAS);
		array.put(BIOLOGICO);
		array.put(JARDINAGEM);
		array.put(MATAS);
		
		Query catQ = new Query(DSUtils.CATEGORY);
		Filter catF = new Query.FilterPredicate(DSUtils.CATEGORY_LIST,
				FilterOperator.EQUAL, true);
		catQ.setFilter(catF);
		
		List<Entity> cats = datastore.prepare(catQ)
				.asList(FetchOptions.Builder.withDefaults());
		
		for(Entity cat : cats)
			array.put(cat.getKey().getName());
		
		return Response.ok(array.toString()).build();
	}
	
	public static List<String> innerList() {
		
		Query catQ = new Query(DSUtils.CATEGORY);
		Filter catF = new Query.FilterPredicate(DSUtils.CATEGORY_LIST,
				FilterOperator.EQUAL, true);
		catQ.setFilter(catF);
		
		List<Entity> cats = datastore.prepare(catQ)
				.asList(FetchOptions.Builder.withDefaults());

		List<String> list = new ArrayList<String>(NUM_DEF + cats.size());

		list.add(LIXO);
		list.add(PESADOS);
		list.add(PERIGOSOS);
		list.add(PESSOAS);
		list.add(TRANSPORTE);
		list.add(MADEIRAS);
		list.add(CARCACAS);
		list.add(BIOLOGICO);
		list.add(JARDINAGEM);
		list.add(MATAS);
		
		for(Entity cat : cats)
			list.add(cat.getKey().getName());
		
		return list;
	}
	
	public static void addOrInc(String category) {
		if(isDef(category))
			return;
		
		Entity cat;
		try {
			cat = datastore.get(KeyFactory.createKey(DSUtils.CATEGORY, category));
			
			long num = (long) cat.getProperty(DSUtils.CATEGORY_NUM);
			
			cat.setProperty(DSUtils.CATEGORY_NUM, num + 1L);
			
			if(num + 1L > TO_LIST)
				cat.setProperty(DSUtils.CATEGORY_LIST, true);
		} catch(EntityNotFoundException e) {					
			cat = new Entity(DSUtils.CATEGORY, category);
			cat.setProperty(DSUtils.CATEGORY_NUM, 1L);
		}
		
		datastore.put(cat);
	}
	
	public static boolean isDef(String category) {
		return category.equals(LIXO) || category.equals(PESADOS) ||category.equals(PERIGOSOS) || category.equals(PESSOAS) ||
				category.equals(TRANSPORTE) || category.equals(CARCACAS) || category.equals(BIOLOGICO) || category.equals(JARDINAGEM) ||
				category.equals(MATAS) || category.equals(MADEIRAS);
	}
}
