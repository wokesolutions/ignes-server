var map = null;
var geocoder = new google.maps.Geocoder();
var reports;
var current_position = "map_variable";
var info_window = new google.maps.InfoWindow();
var current_location = {
    center: {lat: 38.661148, lng: -9.203075},
    zoom: 18
};

var URL_BASE = 'https://hardy-scarab-200218.appspot.com';

google.maps.event.addDomListener(window, 'load', init());


function init() {

    verifyIsLoggedIn();

    document.getElementById("search_location").onclick = searchLocation;
    document.getElementById('map_button').onclick = showMap;
    document.getElementById("profile_button").onclick = showProfile;
    document.getElementById("feed_button").onclick = showFeed;
    document.getElementById("user_table_button").onclick = showWorkers;
    document.getElementById("create_button").onclick = showCreateWorker;
    document.getElementById("worker_register").onclick = createWorker;
    document.getElementById("logout_button").onclick = logOut;

    getMarkers("Caparica");

    var mapElement = document.getElementById('map');
    map = new google.maps.Map(mapElement, current_location);

}

function searchLocation(){
    var address = document.getElementById('location').value;
    geocoder.geocode( { 'address': address}, function(results, status) {
        if (status == 'OK') {
            map.setCenter(results[0].geometry.location);
            map.setZoom(15);
        } else {
            alert('A morada inserida não existe.');
        }
    });

    getMarkers(address);
}

function hideShow(element){

    if(current_position === "map_variable"){

        document.getElementById("map").style.display = "none";
        document.getElementById("search_location_style").style.display = "none";

    }else if(current_position === "profile_variable"){

        document.getElementById("perfilId").style.display = "none";

    }else if(current_position === "feed_variable"){

        document.getElementById("feedId").style.display = "none";

    }else if(current_position === "users_variable"){

        document.getElementById("list_users").style.display = "none";

    }else if(current_position == "create_variable"){

        document.getElementById("create_worker").style.display = "none";
    }


    if(element === "map_variable"){

        document.getElementById("map").style.display = "block";
        document.getElementById("search_location_style").style.display = "block";
        current_position = "map_variable";

    }else if(element === "profile_variable"){

        document.getElementById("perfilId").style.display = "block";
        current_position = "profile_variable";

    }else if(element === "feed_variable"){

        document.getElementById("feedId").style.display = "block";
        current_position = "feed_variable";

    }else if(element === "users_variable"){

        document.getElementById("list_users").style.display = "block";
        current_position = "users_variable";

    }else if(element === "create_variable"){

        document.getElementById("create_worker").style.display = "block";
        current_position = "create_variable";

    }

}

function verifyIsLoggedIn(){
    console.log(localStorage.getItem('token'));
    fetch(URL_BASE + '/api/verifytoken', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status !== 200) {

                window.location.href = "index.html";

            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });



}

function logOut(){
    console.log(localStorage.getItem('token'));
    fetch(URL_BASE + '/api/logout/org', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {
                localStorage.removeItem('token');
                localStorage.removeItem('ignes_username');
                window.location.href = "index.html";

            }else{
                console.log("Tratar do Forbidden")
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });



}

function getMarkers(address){
    fetch(URL_BASE + '/api/report/getinlocation?location=' + address + '&offset=0&', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        }
    }).then(function(response) {

            if (response.status === 200) {
                response.json().then(function(data) {
                    reports = data;
                    console.log(reports);
                    fillMap(reports);
                });

            }else{
                console.log("Tratar do Forbidden")
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });

}

function fillMap(reports){
    var i, marker ;
    for(i = 0; i<reports.length; i++){
        var lat = reports[i].report_lat;
        var lng = reports[i].report_lng;

        console.log(lat + " " + lng);

        marker = new google.maps.Marker({
            position: new google.maps.LatLng(lat, lng),
            map: map
        });


        google.maps.event.addListener(marker, 'click', (function(marker, i) {

            return function() {
                var contentString = '<div id="content">'+
                    '<h1 style="font-family: Quicksand Bold; color:#AD363B; font-size:30px">'+ reports[i].report_title +'</h1>'+ '<div>' +
                    '<p style="font-family: Quicksand Bold">'+'Localização' +'</p>'+ '<p>' + reports[i].report_address + '</div>'+
                    '<div>' +
                    '<p style="font-family: Quicksand Bold">'+'Descrição' + '<p>' + reports[i].report_description +'</p>'+ '</p>' +'</div>'+
                    '<div>'+
                    '<p style="font-family: Quicksand Bold">'+'Estado' +'</p>'+ '<p style="color:forestgreen">' + reports[i].report_status +
                    '</div>'+
                    '</div>';
                info_window.setContent(contentString);
                info_window.open(map, marker);
            }
        })(marker, i));
    }
}

function showMap(){
    hideShow('map_variable');
}

function showProfile() {
    hideShow('profile_variable');

}

function showFeed() {
    hideShow('feed_variable');

}

function showWorkers(){
    getWorkers();
    hideShow('users_variable');
}

function showCreateWorker(){
    hideShow('create_variable');
}

function getWorkers(){
    var info;
    fetch(URL_BASE + '/api/org/listworkers', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {

                response.json().then(function(data) {
                    console.log(JSON.stringify(data));
                    if(data != null){
                        var i;
                        var table = document.getElementById("user_table");
                        if(table.rows.length > 1)
                            clearTable();
                        for(i = 0; i < data.length; i++){
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            cell1.innerHTML = data[i].worker_name;
                            cell2.innerHTML = data[i].Worker;
                            cell3.innerHTML = data[i].worker.job;
                        }

                    }else{
                        alert("Esta empresa ainda não tem trabalhadores associados.")
                    }
                });

            }else{
                console.log("Tratar do Forbidden")
                return info;
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
            return info;
        });

}

function clearTable(){
    var table = document.getElementById("user_table");
    var i;
    for(i = 1; i < table.rows.length; i++){
        table.deleteRow(i);
    }
}

function createWorker(){
    var username = document.getElementById("worker_username").value;
    var email = document.getElementById("worker_email").value;
    var job = document.getElementById("worker_jobs").value;

    fetch(URL_BASE + '/api/org/registerworker', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        },
        body: JSON.stringify({
            worker_username: username,
            worker_email: email,
            worker_job: job
        })
    }).then(function(response) {

            if (response.status === 200) {
                alert("Trabalhador registado com sucesso.")
            }else{
                alert("Utilizador já existe ou falta informação em algum campo")
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}
