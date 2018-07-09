package com.wokesolutions.ignes.util;

import java.util.HashMap;
import java.util.Map;

public class ProfanityFilter {
	
	private Map<String, Object> map;
	
	public ProfanityFilter() {
		init();
	}
	
	private void init() {
		map = new HashMap<String, Object>(64, 1);
		
		map.put("cabrão", null);
		map.put("cabrao", null);
		map.put("Cabrao", null);
		map.put("Cabrão", null);
		
		map.put("puta", null);
		map.put("Puta", null);
		
		map.put("cona", null);
		map.put("Cona", null);
		
		map.put("caralho", null);
		map.put("Caralho", null);
		
		map.put("Pila", null);
		map.put("pila", null);
		
		map.put("Piça", null);
		map.put("piça", null);
		
		map.put("Pixa", null);
		map.put("pixa", null);
		
		map.put("foda", null);
		map.put("Foda", null);
		
		map.put("Foda-se", null);
		map.put("foda-se", null);

		map.put("fodase", null);
		map.put("Fodase", null);
		
		map.put("Cu", null);
		map.put("cu", null);
		
		map.put("Fodilhão", null);
		map.put("fodilhão", null);
		map.put("Fodilhao", null);
		map.put("fodilhao", null);

		map.put("culhão", null);
		map.put("culhao", null);
		map.put("Culhão", null);
		map.put("culhao", null);
		map.put("colhao", null);
		map.put("Colhão", null);
		map.put("colhao", null);
		map.put("colhao", null);
		
		map.put("Xaroca", null);
		map.put("xaroca", null);
		
		map.put("Meita", null);
		map.put("meita", null);
		
		map.put("Merda", null);
		map.put("merda", null);

		map.put("Buceta", null);
		map.put("buceta", null);

		map.put("Mata-te", null);
		map.put("mata-te", null);
		
		map.put("Fuck", null);
		map.put("fuck", null);
		
		map.put("Bitch", null);
		map.put("bitch", null);
		
		map.put("Pussy", null);
		map.put("pussy", null);
		
		map.put("Cock", null);
		map.put("cock", null);
		
		map.put("Paneleiro", null);
		map.put("paneleiro", null);
		
		map.put("Foder", null);
		map.put("foder", null);
		
		map.put("Pêga", null);
		map.put("pêga", null);
		
		map.put("Xupa-mos", null);
		map.put("xupa-mos", null);
		map.put("Chupa-mos", null);
		map.put("Chupa-mos", null);
		
		map.put("Safoda", null);
		map.put("safoda", null);
		
		map.put("Dildo", null);
		map.put("dildo", null);
		
		map.put("Fodasse", null);
		map.put("fodasse", null);
		
		map.put("fdp", null);
		map.put("Fdp", null);
		
		map.put("crl", null);
		map.put("Crl", null);
	}
	
	public String filter(String text) {
		String[] split = text.split(" ");
		
		for(int i = 0; i < split.length; i++)
			if(map.containsKey(split[i])) {
				char[] str = new char[split[i].length()];
				for(int j = 0; j < str.length; j++)
						str[j] = '*';
				
				String ast = new String(str);
				
				split[i] = ast;
			}
		
		StringBuilder filtered = new StringBuilder();
		for(int i = 0; i < split.length; i++)
			if(i == split.length - 1)
				filtered.append(split[i]);
			else
				filtered.append(split[i] + " ");
		
		return filtered.toString();
	}
}
