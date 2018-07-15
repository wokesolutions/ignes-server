package com.wokesolutions.ignes.util;

public class Category {
	
	public static final String LIMPEZA = "LIMPEZA"; // Limpeza de terrenos
	public static final String COMBUSTIVEL = "COMBUSTIVEL"; // Material combustível
	public static final String ELETRICIDADE = "ELETRICIDADE"; // Material elétrico
	
	public static boolean isEq(String category) {
		return category.equals(LIMPEZA) || category.equals(COMBUSTIVEL) ||
				category.equals(ELETRICIDADE);
	}
}
