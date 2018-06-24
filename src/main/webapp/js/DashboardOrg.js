var map = null;
var geocoder = new google.maps.Geocoder();
var reports;
var current_position = "map_variable";
var infowindow = new google.maps.InfoWindow();
var currentLoc ={
    center: {lat: 38.661148, lng: -9.203075},
    zoom: 18
};

getCurrentLocation();

var URL_BASE = 'https://hardy-scarab-200218.appspot.com';

google.maps.event.addDomListener(window, 'load', init());


function init() {

    verifyIsLoggedIn();

    document.getElementById("search_location").onclick = searchLocation;
    document.getElementById('map_button').onclick = showMap;
    document.getElementById("profile_button").onclick = showProfile;
    document.getElementById("user_table_button").onclick = showWorkers;
    document.getElementById("create_button").onclick = showCreateWorker;
    document.getElementById("report_occurrence").onclick = createWorker;
    document.getElementById("logout_button").onclick = logOut;
    document.getElementById("next_list").onclick = getNextWorkers;
    document.getElementById("previous_list").onclick = getPreWorkers;
    document.getElementById("refresh_workers").onclick = getFirstWorkers;

    getFirstWorkers();

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

    getMarkersByLocation(address);
}

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

function hideShow(element){

    if(current_position === "map_variable"){

        document.getElementById("map_search").style.display = "none";


    }else if(current_position === "profile_variable"){

        document.getElementById("profile").style.display = "none";

    }else if(current_position === "users_variable"){

        document.getElementById("list_users").style.display = "none";

    }else if(current_position == "create_variable"){

        document.getElementById("create_worker").style.display = "none";
    }


    if(element === "map_variable"){

        document.getElementById("map_search").style.display = "block";
        current_position = "map_variable";

    }else if(element === "profile_variable"){

        document.getElementById("profile").style.display = "block";
        current_position = "profile_variable";

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
    fetch(URL_BASE + '/api/logout', {
        method: 'POST',
        headers: {
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

function getMarkersByLocation(zone, cursor){
    if(cursor===undefined) cursor = "";
    fetch(URL_BASE + '/api/report/getinlocation?' + "location=" + zone + "&cursor=" + cursor, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {
                var newCursor = response.headers.get("Cursor");
                response.json().then(function(data) {
                    reports = data;
                    fillMap(reports, newCursor, zone);
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
                response.json().then(function(data) {
                    reports = data;
                    console.log(data);
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

function fillMap(reports, cursor, zone){
    var i, marker ;
    for(i = 0; i<reports.length; i++){
        var lat = reports[i].report_lat;
        var lng = reports[i].report_lng;


        marker = new google.maps.Marker({
            position: new google.maps.LatLng(lat, lng),
            map: map
        });


        google.maps.event.addListener(marker, 'click', (function(marker, i) {

            return function() {
                getInfo(reports[i].Report, i);

            }
        })(marker, i));
    }

    if(cursor !== null){
        if(zone === null) {
            console.log(cursor);
            getMarkers(5, cursor);
        } else{
            getMarkersByLocation(zone, cursor);
        }
    }
}

function getInfo(idReport, i){
    fetch(URL_BASE + '/api/report/thumbnail/' + idReport, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {
                var image = new Image();
                image.src = 'data:image/png;base64,' + getThumbnailById(idReport);
                document.body.appendChild(image);

                if(reports[i].report_title !== null)
                    document.getElementById('report_title_id').innerHTML= reports[i].report_title;

                document.getElementById('report_address_id').innerHTML= reports[i].report_address;
                document.getElementById('report_description_id').innerHTML= reports[i].report_description;
                document.getElementById('report_state_id').innerHTML= reports[i].report_status;

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

function showMap(){
    hideShow('map_variable');
}

function showProfile() {
    getProfile();
    hideShow('profile_variable');

}

function showWorkers(){
    hideShow('users_variable');
}

function showCreateWorker(){
    hideShow('create_variable');
    document.getElementById("org_name").innerHTML = localStorage.getItem('ignes_org_name');
}

function createWorker(){
    var name = document.getElementById("worker_username").value;
    var email = document.getElementById("worker_email").value;
    var job = document.getElementById("worker_jobs").value;

    fetch(URL_BASE + '/api/org/registerworker', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        },
        body: JSON.stringify({
            worker_name: name,
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

function getFirstWorkers(){
    fetch(URL_BASE + '/api/org/listworkers?cursor=', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(response.headers.get("Cursor") !== null) {
                    console.log("Existe cursor");
                    cursor_pre_workers = "";
                    cursor_current_workers = "";
                    cursor_next_workers = response.headers.get("Cursor");
                    if(document.getElementById("next_list").style.display === "none")
                        document.getElementById("next_list").style.display = "block";
                    if(document.getElementById("previous_list").style.display === "block")
                        document.getElementById("previous_list").style.display = "none";
                } else{
                    if(document.getElementById("next_list").style.display === "block")
                        document.getElementById("next_list").style.display = "none";
                    if(document.getElementById("previous_list").style.display === "block")
                        document.getElementById("previous_list").style.display = "none";
                }
                response.json().then(function(data) {
                    console.log(JSON.stringify(data));
                    if(data != null){
                        var i;
                        for(i = 0; i < data.length; i++){
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            var cell4 = row.insertCell(3);
                            cell1.innerHTML = data[i].worker_name;
                            cell2.innerHTML = data[i].Worker;
                            cell3.innerHTML = data[i].worker_job;
                            cell4.outerHTML= "<button type='submit' class='btn-circle btn-primary-style' onclick='deleteWorker(this.parentNode.rowIndex)'><p class='delete_style'>X</p></button>";

                        }

                    }else{
                        alert("Esta empresa ainda não tem trabalhadores associados.")
                    }
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

function getNextWorkers(){
    fetch(URL_BASE + '/api/org/listworkers?cursor=' + cursor_next_workers, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(document.getElementById("previous_list").style.display === "none")
                    document.getElementById("previous_list").style.display = "block";
                if(response.headers.get("Cursor") !== null) {

                    cursor_pre_workers = cursor_current_workers;
                    cursor_current_workers = cursor_next_workers;
                    cursor_next_workers = response.headers.get("Cursor");

                    if(document.getElementById("next_list").style.display === "none")
                        document.getElementById("next_list").style.display = "block";

                } else{
                    if(document.getElementById("next_list").style.display === "block")
                        document.getElementById("next_list").style.display = "none";
                }
                response.json().then(function(data) {
                    console.log(JSON.stringify(data));
                    if(data != null){
                        var i;
                        for(i = 0; i < data.length; i++){
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            var cell4 = row.insertCell(3);
                            cell1.innerHTML = data[i].worker_name;
                            cell2.innerHTML = data[i].Worker;
                            cell3.innerHTML = data[i].worker_job;
                            cell4.outerHTML= "<button type='submit' class='btn-circle btn-primary-style' onclick='deleteWorker(this.parentNode.rowIndex)'><p class='delete_style'>X</p></button>";
                        }

                    }else{
                        alert("Esta empresa ainda não tem trabalhadores associados.")
                    }
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

function getPreWorkers(){
    if(cursor_pre_workers === "") getFirstWorkers();
    else{
        fetch(URL_BASE + '/api/org/listworkers?cursor=' + cursor_pre_workers, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': localStorage.getItem('token')
            }
        }).then(function(response) {
                var table = document.getElementById("user_table");

                if (response.status === 200) {
                    if(table.rows.length > 1) {
                        table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                    }
                    if(document.getElementById("previous_list").style.display === "none")
                        document.getElementById("previous_list").style.display = "block";
                    if(response.headers.get("Cursor") !== null) {

                        cursor_next_workers = cursor_current_workers;
                        cursor_current_workers = cursor_pre_workers;
                        cursor_pre_workers = response.headers.get("Cursor");

                        if(document.getElementById("next_list").style.display === "none")
                            document.getElementById("next_list").style.display = "block";

                    } else{
                        if(document.getElementById("next_list").style.display === "block")
                            document.getElementById("next_list").style.display = "none";
                    }
                    response.json().then(function(data) {
                        console.log(JSON.stringify(data));
                        if(data != null){
                            var i;
                            for(i = 0; i < data.length; i++){
                                var row = table.insertRow(-1);
                                var cell1 = row.insertCell(0);
                                var cell2 = row.insertCell(1);
                                var cell3 = row.insertCell(2);
                                var cell4 = row.insertCell(3);
                                cell1.innerHTML = data[i].worker_name;
                                cell2.innerHTML = data[i].Worker;
                                cell3.innerHTML = data[i].worker_job;
                                cell4.outerHTML= "<button type='submit' class='btn-circle btn-primary-style' onclick='deleteWorker(this.parentNode.rowIndex)'><p class='delete_style'>X</p></button>";
                            }

                        }else{
                            alert("Esta empresa ainda não tem trabalhadores associados.")
                        }
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
}

function getProfile(){
    fetch(URL_BASE + '/api/org/info/' + localStorage.getItem('ignes_org_nif'), {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200) {
                response.json().then(function(data) {

                    document.getElementById("organization_name").innerHTML = localStorage.getItem('ignes_org_name');
                    document.getElementById("organization_nif").innerHTML = data.Org;
                    document.getElementById("organization_email").innerHTML = data.org_email;
                    document.getElementById("organization_addresses").innerHTML = data.org_address;
                    document.getElementById("organization_locality").innerHTML = data.org_locality;
                    document.getElementById("organization_zip").innerHTML = data.org_zip;
                    document.getElementById("organization_service").innerHTML = data.org_services;
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

function deleteWorker (row){
    var email = document.getElementById("user_table").rows[row].cells[1].innerHTML;
    fetch(URL_BASE + '/api/org/deleteworker/' + email, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

            if (response.status === 200 || response.status === 204) {
                alert("Trabalhador apagado com sucesso.")
            }else{
                alert("Falha ao apagar o utilizador.")
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

function getThumbnailById(idReport){
    fetch(URL_BASE + '/api/report/thumbnail/' + idReport, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    }).then(function(response) {

            if (response.status === 200) {
                response.json().then(function(data) {
                    return data.report_thumbnail;
                });
            }
        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

var max = 20;

var loadMore = function () {
    for (var i = max-20; i < max; i++) {
        $(".inner").append("<p>test "+i+"</p>");
    }
    max += 20;
}

$('.on').scroll(function () {
    var top = $('.on').scrollTop();
    $('.two').html("top: "+top+" diff: "+($(".inner").height() - $(".on").height()));
    if (top >= $(".inner").height() - $(".on").height()) {
        $('.two').append(" bottom");
        loadMore();
    }
});

loadMore();
