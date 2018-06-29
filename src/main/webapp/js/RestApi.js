var URL_BASE = 'https://hardy-scarab-200218.appspot.com';

function restRequest(url, method, headers, body){
    if(body==="") {
        headers.append('Content-Type', 'application/json');
        var request = new Request(URL_BASE + url, {
            method: method,
            mode: 'no-cors',
            headers: headers,
        });
    }else{
        headers.append('Content-Type', 'application/json');
        var request = new Request(URL_BASE + url, {
            method: method,
            mode: 'no-cors',
            headers: headers,
            body: body
        });
    }

    return request;
}