var map = null;

var URL_BASE = 'https://hardy-scarab-200218.appspot.com';

var geocoder = new google.maps.Geocoder();

var currentLoc = {
    center: {lat: 38.661148, lng: -9.203075},
    zoom: 18
};

getCurrentLocation();

var reports;
var current_position = "map_variable";
var previewImageBase = "";
var infowindow = new google.maps.InfoWindow();

google.maps.event.addDomListener(window, 'load', init());


function init() {

    verifyIsLoggedIn();

    document.getElementById("search_location").onclick = searchLocation;
    document.getElementById("report_button").onclick = showReport;
    document.getElementById("logout_button").onclick = logOut;
    document.getElementById("report_occurrence").onclick = addReport;
    document.getElementById("map_button").onclick = showMap;
    document.getElementById("profile_button").onclick = showProfile;
    document.getElementById("feed_button").onclick = showFeed;
    document.getElementById("space_button").onclick = showNeighborsSpace;
    document.getElementById("contact_button").onclick = showContacts;
    document.getElementById("button_edit").onclick = setProfile;
    document.getElementById('imagem').onchange = encodeImageFileAsURL;


}
//Tambem inicializa o mapa
function getCurrentLocation() {

    if(navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(function (position) {
            console.log(currentLoc);
            currentLoc = {
                center: {lat: position.coords.latitude, lng: position.coords.longitude},
                zoom: 15
            };
            console.log(currentLoc);

            var mapElement = document.getElementById('map');
            map = new google.maps.Map(mapElement, currentLoc);

            getMarkers(5);
        })
    }else {
        var mapElement = document.getElementById('map');
        map = new google.maps.Map(mapElement, currentLoc);

        getMarkers(5);
    }

    return currentLoc;
}

function searchLocation(){
    var address = document.getElementById('location').value;
    geocoder.geocode( { 'address': address}, function(results, status) {
        if (status == 'OK') {
            map.setCenter(results[0].geometry.location);
            map.setZoom(15);
        } else {
            alert('A morada inserida não existe');
        }
    });

    getMarkers(address);
}

function hideShow(element){


    if(current_position === "map_variable"){

        document.getElementById("map").style.display = "none";
        document.getElementById("search_location_style").style.display = "none";

    }else if(current_position === "report_variable"){

        document.getElementById("report_form").style.display = "none";

    }else if(current_position === "space_variable"){

        document.getElementById("space_neighbor").style.display = "none";

    }else if(current_position === "profile_variable"){

        document.getElementById("profile").style.display = "none";

    }else if(current_position === "feed_variable"){

        document.getElementById("feed").style.display = "none";

    }else if(current_position === "contacts_variable"){

        document.getElementById("contacts").style.display = "none";

    }

    if(element === "map_variable"){

        document.getElementById("map").style.display = "block";
        document.getElementById("search_location_style").style.display = "block";
        current_position = "map_variable";

    }else if(element === "report_variable"){

        document.getElementById("report_form").style.display = "block";
        current_position = "report_variable";

    }else if(element === "space_variable"){

        document.getElementById("space_neighbor").style.display = "block";
        current_position = "space_variable";

    }else if(element === "profile_variable"){

        document.getElementById("profile").style.display = "block";
        current_position = "profile_variable";

    }else if(element === "feed_variable"){

        document.getElementById("feed").style.display = "block";
        current_position = "feed_variable";

    }else if(element === "contacts_variable"){

        document.getElementById("contacts").style.display = "block";
        current_position = "contacts_variable";

    }

}

function showReport(){
    hideShow('report_variable');
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
                window.location.href = "../index.html";

            }else{
                console.log("Tratar do Forbidden")
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });



}

function addReport(){
    var title = document.getElementById("titulo").value;
    //var private = document.getElementById("state").value;
    var description = document.getElementById("descricao").value;
    var address = document.getElementById("address").value;
    var gravity = document.getElementById("gravityId").value;
    var marker;
    var bodyToSend = {};

    if(previewImageBase !== ""){
        geocoder.geocode( { 'address': address}, function(results, status) {
            if(status == 'OK'){

                var lat = results[0].geometry.location.lat();
                var lng = results[0].geometry.location.lng();

                bodyToSend.report_lat = lat;
                bodyToSend.report_lng = lng;
                bodyToSend.report_img = previewImageBase;
                bodyToSend.report_private = true;
                bodyToSend.report_gravity = gravity;

                if(title !== "" || title !== undefined){
                    bodyToSend.report_title = title;
                }
                if(description !== null || description !== undefined){
                    bodyToSend.report_description = description;
                }

                fetch('https://hardy-scarab-200218.appspot.com/api/report/create', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': localStorage.getItem('token')
                    },
                    body:bodyToSend


                }).then(function(response) {

                        if (response.status === 200) {

                            marker = new google.maps.Marker({
                                position: new google.maps.LatLng(lat, lng),
                                map: map
                            });

                            google.maps.event.addListener(marker, 'click', (function(marker, i) {
                                return function() {
                                    var contentString = '<div id="content">'+
                                        '<h1 style="font-family: Quicksand Bold; color:#AD363B; font-size:30px">'+ title +'</h1>'+ '<div>' +
                                        '<p style="font-family: Quicksand Bold">'+'Localização' +'</p>'+ '<p>' + address + '</div>'+
                                        '<div>' +
                                        '<p style="font-family: Quicksand Bold">'+'Descrição' + '<p>' + description +'</p>'+ '</p>' +'</div>'+
                                        '<div>'+
                                        '<p style="font-family: Quicksand Bold">'+'Estado' +'</p>'+ '<p style="color:forestgreen">' + "OPEN" +
                                        '</div>'+
                                        '</div>';
                                    infowindow.setContent(contentString);
                                    infowindow.open(map, marker);
                                }
                            })(marker, i));
                            bodyToSend = "";
                            map.setCenter(new google.maps.LatLng(lat, lng));
                            showMap();
                        }

                    }
                ).catch(function(err) {
                    console.log('Fetch Error', err);
                });
            }else{
                alert("A morada inserida não existe.")
            }
        });
    }else{
        alert("É necessária uma imagem para concluir o report.")
    }

}

function getMarkers(radius, cursor){
    if(cursor===undefined) cursor = "";
    fetch(URL_BASE + '/api/report/getwithinradius?' + "lat=" + currentLoc.center.lat + "&lng=" + currentLoc.center.lng +
        "&radius=" + radius + "&cursor=" + cursor, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {
                var newCursor = response.headers.get("Cursor");
                console.log(newCursor);
                response.json().then(function(data) {
                    reports = data;
                    console.log(reports);
                    fillMap(reports, newCursor);
                });

            }else{
                console.log("Tratar do Forbidden");
                return;
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });

}

function fillMap(reports, cursor){
    var i, marker ;
    for(i = 0; i<reports.length; i++){
        var lat = reports[i].report_lat;
        var lng = reports[i].report_lng;

        console.log(cursor);

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
                infowindow.setContent(contentString);
                infowindow.open(map, marker);
            }
        })(marker, i));
        if(cursor !== null || cursor !== undefined){
            getMarkers(5, cursor);
        }

    }
}

function showMap(){
    hideShow('map_variable');
}

function showProfile() {
    getProfile();
    hideShow('profile_variable');
}

function showFeed() {
    hideShow('feed_variable');
}

function showNeighborsSpace() {
    hideShow('space_variable');

}

function showContacts() {
    hideShow('contacts_variable');
}

function getProfile(){
    fetch(URL_BASE + '/api/profile/view/' + localStorage.getItem('ignes_username'), {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {
                response.json().then(function(data) {

                    document.getElementById("num_points").innerHTML = data.userpoints_points;
                    document.getElementById("num_reports").innerHTML = data.user_reports;
                    document.getElementById("people_username").innerHTML = data.User;
                    document.getElementById("num_level").innerHTML = data.user_level;
                    document.getElementById("people_email").innerHTML = data.user_email;

                    if(data.useroptional_name!== undefined || data.useroptional_name!== "")
                        document.getElementById("people_name").innerHTML = data.useroptional_name;
                    else
                        document.getElementById("people_name").innerHTML = "-";


                    if(data.useroptional_birth !== undefined || data.useroptional_birth !== "" )
                        document.getElementById("people_birthday").innerHTML = data.useroptional_birth;
                    else
                        document.getElementById("people_birthday").innerHTML = "-";

                    /*  if(data.useroptional_locality !== undefined)
                          document.getElementById("people_locality").innerHTML = data.useroptional_locality;
                      else
                          document.getElementById("people_locality").innerHTML = "-";
                    */
                    if(data.useroptional_phone !== undefined || data.useroptional_phone !== "" )
                        document.getElementById("people_phone").innerHTML = data.useroptional_phone;
                    else
                        document.getElementById("people_phone").innerHTML = "-";

                    if(data.useroptional_address !== undefined || data.useroptional_address !== "")
                        document.getElementById("people_address").innerHTML = data.useroptional_address;
                    else
                        document.getElementById("people_address").innerHTML = "-";

                    /*  if(data.useroptional_zip !== undefined)
                       document.getElementById("people_cp").innerHTML = data.useroptional_zip;
                    else
                        document.getElementById("people_cp").innerHTML = "-";
                    */
                    if(data.useroptional_gender !== undefined || data.useroptional_gender !== "")
                        document.getElementById("people_gender").innerHTML = data.useroptional_gender;
                    else
                        document.getElementById("people_gender").innerHTML = "-";

                    if(data.useroptional_job !== undefined || data.useroptional_job !== "")
                        document.getElementById("people_job").innerHTML = data.useroptional_job;
                    else
                        document.getElementById("people_job").innerHTML = "-";

                    if(data.useroptional_skills !== undefined || data.useroptional_skills !== "")
                        document.getElementById("people_skills").innerHTML = data.useroptional_skills;
                    else
                        document.getElementById("people_skills").innerHTML = "-";

                });

            }else{
                console.log("Tratar do Forbidden");
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });

}

function setProfile(){

    var user_name = document.getElementById('input_name').value;
    var user_gender= document.getElementById('input_gender').value;
    var user_birth = document.getElementById('input_birth').value;
    var user_address = document.getElementById('input_address').value;
    var user_phone = document.getElementById('input_phone').value;
    var user_job = document.getElementById('input_job').value;
    var user_skills = document.getElementById('input_skills').value;


    var propsThatExist = {};

    if(user_name != null || user_name != "")
        propsThatExist.useroptional_name = user_name;
    if(user_gender != null || user_gender != "")
        propsThatExist.useroptional_gender = user_gender;
    if(user_birth != null || user_birth != "")
        propsThatExist.useroptional_birth = user_birth;
    if(user_address != null || user_address != "")
        propsThatExist.useroptional_address = user_address;
    if(user_phone != null || user_phone != "")
        propsThatExist.useroptional_phone = user_phone;
    if(user_job != null || user_job != "")
        propsThatExist.useroptional_job = user_job;
    if(user_skills != null || user_skills != "")
        propsThatExist.useroptional_skills = user_skills;

    fetch(URL_BASE + '/api/profile/update/' + localStorage.getItem('ignes_username'), {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        },
        body:JSON.stringify(propsThatExist)
    }).then(function(response) {
            console.log("Fiz");
            if (response.status === 200) {

                console.log("ola");
            }else{
                console.log("Tratar do Forbidden");
            }


        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });

}

function encodeImageFileAsURL() {

    var filesSelected = document.getElementById("imagem").files;
    var preview = document.getElementById('img');
    if (filesSelected.length > 0) {
        var file = filesSelected[0];

        var reader = new FileReader();

        reader.onloadend = function() {
            previewImageBase = reader.result.substring(reader.result.indexOf(",") + 1);
            preview.src = reader.result;
        }
        reader.readAsDataURL(file);
    }
}