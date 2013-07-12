package org.intermine.webservice.server.exceptions;

import java.net.HttpURLConnection;

public class MethodNotAllowed extends ServiceException {

    public MethodNotAllowed(String method) {
        super(method + " method is not allowed");
        initErrorCode();
    }

    private void initErrorCode() {
        setHttpErrorCode(HttpURLConnection.HTTP_BAD_METHOD);
    }
}
