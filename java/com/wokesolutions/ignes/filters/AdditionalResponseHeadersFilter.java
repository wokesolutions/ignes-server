package com.wokesolutions.ignes.filters;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import com.wokesolutions.ignes.util.Message;

@Priority(3)
public class AdditionalResponseHeadersFilter implements Filter {
	public static final Logger LOG = Logger.getLogger(AdditionalResponseHeadersFilter.class.getName());
	
	public void init(FilterConfig arg0) throws ServletException {}  

	public void doFilter(ServletRequest req, ServletResponse resp,  
			FilterChain chain) throws IOException, ServletException {

		LOG.info(this.getClass().getSimpleName() + Message.FILTER_VERIFYING);
		((HttpServletResponse) resp).setHeader("Access-Control-Expose-Headers", "*");
		((HttpServletResponse) resp).setHeader("Access-Control-Allow-Origin", "*");
		((HttpServletResponse) resp).setHeader("Access-Control-Allow-Headers",
				"Content-Type, X-Requested-With, Authorization, Device-ID, Device-Info, Device-App");
		((HttpServletResponse) resp).setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");
		
		chain.doFilter(req, resp);
	}

	public void destroy() {}

}
