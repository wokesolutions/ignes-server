package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ext.Provider;

import com.wokesolutions.ignes.util.Message;

@Provider

public class AdditionalResponseHeadersFilter implements Filter {
	public static final Logger LOG = Logger.getLogger(AdditionalResponseHeadersFilter.class.getName());
	
	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {

		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING);
		((HttpServletResponse) resp).setHeader("Access-Control-Expose-Headers", "*");
		((HttpServletResponse) resp).setHeader("Access-Control-Allow-Origin", "https://wokesolutionsignes.com");
		((HttpServletResponse) resp).setHeader("Access-Control-Allow-Headers",
				"Content-Type, X-Requested-With, Authorization");
		
		chain.doFilter(req, resp);
	}

	public void destroy() {}

}
