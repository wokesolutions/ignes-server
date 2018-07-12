var URL_BASE = 'https://main-dot-mimetic-encoder-209111.appspot.com';

function restRequest(url, method, headers, body){
    if(body==="") {
        headers.append('Content-Type', 'application/json');
        var request = new Request(URL_BASE + url, {
            method: method,
            headers: headers,
        });
    }else{
        headers.append('Content-Type', 'application/json');
        var request = new Request(URL_BASE + url, {
            method: method,
            headers: headers,
            body: body
        });
    }

    return request;
}