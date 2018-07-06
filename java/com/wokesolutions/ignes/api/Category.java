package com.wokesolutions.ignes.api;

public class Category {
	
	public static final String LIXO = "LIXO";
	public static final String PESADOS = "PESADOS";
	public static final String PERIGOSOS = "PERIGOSOS";
	public static final String PESSOAS = "PESSOAS";
	public static final String TRANSPORTE = "TRANSPORTE";
	public static final String MADEIRAS = "MADEIRAS";
	public static final String CARCACAS = "CARCACAS";
	public static final String BIOLOGICO = "BIOLOGICO";
	public static final String JARDINAGEM = "JARDINAGEM";
	public static final String MATAS = "MATAS";
	
	public static boolean isEq(String category) {
		return category.equals(LIXO) || category.equals(PESADOS)
				|| category.equals(PERIGOSOS) || category.equals(PESSOAS)
				|| category.equals(TRANSPORTE) || category.equals(CARCACAS)
				|| category.equals(BIOLOGICO) || category.equals(JARDINAGEM)
				|| category.equals(MATAS) || category.equals(MADEIRAS);
	}
}
