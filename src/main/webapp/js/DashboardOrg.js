var map = null;
var geocoder = new google.maps.Geocoder();
var reports;
var current_position = "map_variable";
var info_window = new google.maps.InfoWindow();
var current_location = {
    center: {lat: 38.661148, lng: -9.203075},
    zoom: 18
};

var URL_BASE = 'https://maria-dot-hardy-scarab-200218.appspot.com';

google.maps.event.addDomListener(window, 'load', init());
google.maps.event.addDomListener(window, 'resize', function() {
    map.setCenter(new google.maps.LatLng(38.6615119,-8.224454));
});

function init() {

    verifyIsLoggedIn();

    document.getElementById("search_location").onclick = searchLocation;
    document.getElementById('map_button').onclick = showMap;
    document.getElementById("profile_button").onclick = showProfile;
    document.getElementById("feed_button").onclick = showFeed;
    document.getElementById("user_table_button").onclick = showWorkers;
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
    fetch(URL_BASE + '/api/logout', {
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
                    if(data != null){
                        var worker_data = '';
                        $.each(data,function(key,value){
                            worker_data += '<tr>';
                            worker_data += '<td>' + value.user_name + '</td>';
                            worker_data += '<td>' + value.Worker + '</td>';
                            worker_data += '</tr>';
                        });
                        $('#user_table').append(worker_data);
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