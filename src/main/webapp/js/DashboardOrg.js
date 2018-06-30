var map = null;
var geocoder = new google.maps.Geocoder();
var reports;
var reportID;
var feedCursor;
var commentsCursor;
var current_position = "map_variable";
var infowindow = new google.maps.InfoWindow();
var idReportCurr;
var currentLoc ={
    center: {lat: 38.661148, lng: -9.203075},
    zoom: 18
};
var email_current;

getCurrentLocation();

var URL_BASE = 'https://hardy-scarab-200218.appspot.com';

google.maps.event.addDomListener(window, 'load', init());


function init() {

    verifyIsLoggedIn();
    getAvailableWorker("");

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
    document.getElementById("show_more_button").onclick = getShowMore;
    document.getElementById("close_window").onclick = closeWindow;
    document.getElementById("add_task").onclick = giveTask;
    $("#email_select").change(function(){
        document.getElementById('email_task').innerHTML= JSON.parse($("#email_select").val()).Worker;
        document.getElementById('name_worker').innerHTML= JSON.parse($("#email_select").val()).worker_name;

    })

    getFirstWorkers();


}

function getShowMore(){
    hideShow("show_more_variable");
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

            getMarkers(15);
        })
    }else {
        var mapElement = document.getElementById('map');
        map = new google.maps.Map(mapElement, currentLoc);

        getMarkers(15);
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

    }else if(current_position === "create_variable"){

        document.getElementById("create_worker").style.display = "none";

    }else if(current_position === "show_more_variable"){

        document.getElementById("details_report").style.display = "none";

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

    }else if(element === "show_more_variable"){
        document.getElementById("details_report").style.display = "block";
        current_position = "show_more_variable";

    }

}

function verifyIsLoggedIn(){
    console.log(localStorage.getItem('token'));
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/verifytoken','GET', headers, body)).then(function(response) {

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
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/logout','POST', headers, body)).then(function(response) {

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

function getMarkersByLocation(zone, cursor){
    if(cursor===undefined) cursor = "";

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/report/getinlocation?' + "location=" + zone + "&cursor=" + cursor,'GET', headers, body)).then(function(response) {

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

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/report/getwithinradius?' + "lat=" + currentLoc.center.lat + "&lng=" + currentLoc.center.lng +
        "&radius=" + radius + "&cursor=" + cursor, 'GET', headers, body)).then(function(response) {

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
        var lat = reports[i].lat;
        var lng = reports[i].lng;
        var marker_color;
        var gravity = reports[i].gravity;

        if(gravity === 1)
            marker_color = "http://maps.google.com/mapfiles/ms/icons/green-dot.png";
        else if(gravity === 2)
            marker_color = "http://maps.google.com/mapfiles/ms/icons/ltblue-dot.png";
        else if(gravity === 3)
            marker_color = "http://maps.google.com/mapfiles/ms/icons/yellow-dot.png";
        else if(gravity === 4)
            marker_color = "http://maps.google.com/mapfiles/ms/icons/orange-dot.png";
        else if(gravity === 5)
            marker_color = "http://maps.google.com/mapfiles/ms/icons/red-dot.png";
        else{
            console.log("Não existe gravidade neste reporte");
        }

        marker = new google.maps.Marker({
            position: new google.maps.LatLng(lat, lng),
            map: map,
            icon: marker_color
        });


        google.maps.event.addListener(marker, 'click', (function(marker, i) {

            return function() {
                reportID = reports[i].report;
                getInfo(reportID, i);

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
    idReportCurr = idReport;

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/report/thumbnail/' + idReport, 'GET', headers, body)).then(function(response) {

            if (response.status === 200) {

                response.json().then(function(data) {
                    var image = document.getElementById("thumb_report");
                    image.src = "data:image/jpg;base64," + data.thumbnail;
                    var image = document.getElementById("thumb_report_2");
                    image.src = "data:image/jpg;base64," + data.thumbnail;
                });


                if(reports[i].title !== ""){
                    document.getElementById('report_title_id').innerHTML= reports[i].title;
                    document.getElementById('report_title_id_2').innerHTML= reports[i].title;
                }
                else {
                    document.getElementById('report_title_id').innerHTML = "-";
                    document.getElementById('report_title_id_2').innerHTML = "-";
                }

                document.getElementById('report_address_id').innerHTML= reports[i].address;
                document.getElementById('report_address_id_2').innerHTML= reports[i].address;

                if(reports[i].description !== "")
                    document.getElementById('report_description_id').innerHTML= reports[i].description;
                else
                    document.getElementById('report_description_id').innerHTML= "-";

                document.getElementById('report_state_id').innerHTML= reports[i].status;
                document.getElementById('report_state_id_2').innerHTML= reports[i].status;
                document.getElementById('report_gravity_id').innerHTML= reports[i].gravity;
                document.getElementById('report_gravity_id_2').innerHTML= reports[i].gravity;

                if(reports[i].private === true)
                    document.getElementById('report_private_id').innerHTML= "Privado";
                else
                    document.getElementById('report_private_id').innerHTML= "Público";

                document.getElementById('report_comments_id').innerHTML= reports[i].comments;
                document.getElementById('report_user_id').innerHTML= reports[i].username;
                document.getElementById('report_creationtime_id').innerHTML= reports[i].creationtime;
                document.getElementById('report_up_id').innerHTML= reports[i].ups;
                document.getElementById('report_down_id').innerHTML= reports[i].downs;

                loadMoreComments(idReport ,"");

            }else{
                console.log("Tratar do Forbidden");
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

function closeWindow(){
    hideShow("map_variable");
}

function createWorker(){
    var name = document.getElementById("worker_username").value;
    var email = document.getElementById("worker_email").value;
    var job = document.getElementById("worker_jobs").value;
    var headers = new Headers();
    var body = {name:name,email:email,job:job};
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/org/registerworker', 'POST', headers, JSON.stringify(body))).then(function(response) {

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
    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/org/listworkers?cursor=', 'GET', headers, body)).then(function(response) {
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
                            cell1.innerHTML = data[i].name;
                            cell2.innerHTML = data[i].worker;
                            cell3.innerHTML = data[i].job;
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
    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/org/listworkers?cursor=' + cursor_next_workers, 'GET', headers, body)).then(function(response) {
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
                            cell1.innerHTML = data[i].name;
                            cell2.innerHTML = data[i].worker;
                            cell3.innerHTML = data[i].job;
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
        var body = "";
        var headers = new Headers();
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));

        fetch(restRequest('/api/org/listworkers?cursor=' + cursor_pre_workers, 'GET', headers, body)).then(function(response) {
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
                                cell1.innerHTML = data[i].name;
                                cell2.innerHTML = data[i].worker;
                                cell3.innerHTML = data[i].job;
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
    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/org/info/' + localStorage.getItem('ignes_org_nif'), 'GET', headers, body)).then(function(response) {

            if (response.status === 200) {
                response.json().then(function(data) {

                    document.getElementById("organization_name").innerHTML = localStorage.getItem('ignes_org_name');
                    document.getElementById("organization_nif").innerHTML = data.org;
                    document.getElementById("organization_email").innerHTML = data.email;
                    document.getElementById("organization_addresses").innerHTML = data.address;
                    document.getElementById("organization_locality").innerHTML = data.locality;
                    document.getElementById("organization_zip").innerHTML = data.zip;
                    document.getElementById("organization_service").innerHTML = data.services;
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

    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/org/deleteworker/' + email, 'DELETE', headers, body)).then(function(response) {

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

var loadMore = function (cursor) {

    if(cursor!==null){
        var body = "";
        var headers = new Headers();
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));
        fetch(restRequest('/api/org/alltasks?cursor=' + cursor, 'GET', headers, body)).then(function(response) {

                if (response.status === 200 || response.status === 204) {
                    feedCursor = response.headers.get("Cursor");
                    response.json().then(function(data) {
                        console.log(data);
                        var i;
                        for(i = 0; i<data.length; i++){

                            var contentString = '<div id="content" style="margin-bottom:2rem; background:#f8f9fa;"> ' +
                                '<div class="row" >' +
                                '<div class="col-lg-3 col-md-3 mx-auto">'+
                                '</div>'+
                                '<div class="col-lg-6 col-md-6 mx-auto text-center" style="margin-top:1rem">' +
                                '<i class="fa fa-map-marker" style="color:#AD363B; font-size: 2rem"> </i>' +
                                '</div>' +
                                '<div class="col-lg-3 col-md-3 mx-auto-"><p class="text-center"style="font-family:Quicksand; font-size:15px; color:#3b4956">'+ data[i].status+'</p></div>'+
                                '</div>' +
                                ' <div class="row" >' + '<div class="col-lg-12 col-md-12 mx-auto text-center">'+
                                '<p style="margin-bottom:0;font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + data[i].address + '</p>' +'</div>' +'</div><hr>'+
                                '<div class="row"><div class="col-lg-6 text-center">' +'<img style="height:10rem;"id=' +i + '>' +
                                '</div><div class="col-lg-6"><p class="info_text_bold_sm text-center">Descrição</p><p class="text-center" style="font-family:Quicksand; font-size:15px; color:#3b4956">'+ data[i].description+' </p>';

                            if(data[i].task_indications === undefined)

                                contentString += '<p class="info_text_bold_sm text-center">Indicações</p><p class="text-center"style="font-family:Quicksand; font-size:15px; color:#3b4956"></p></div></div><hr style="margin-bottom: 0; margin-top:0">'+
                                    '<div class="row"><div class="col-lg-6 text-left">'+'<p style="margin-left:5rem;font-family:Quicksand bold; font-size:15px; color:#3b4956">' +data[i].task + '</p></div>'+
                                    '<div class="col-lg-6 text-right"><p style="margin-right:3rem;font-family:Quicksand Bold; font-size:15px; color:#3b4956">'+ data[i].creationtime+' </p></div></div>';
                            else
                                contentString += '<p class="info_text_bold_sm text-center">Indicações</p><p class="text-center"style="font-family:Quicksand; font-size:15px; color:#3b4956">'+ data[i].indications+' </p></div></div><hr style="margin-bottom: 0; margin-top:0">'+
                                    '<div class="row"><div class="col-lg-6 text-left">'+'<p style="margin-left:5rem;font-family:Quicksand bold; font-size:15px; color:#3b4956">' +data[i].task + '</p></div>'+
                                    '<div class="col-lg-6 text-right"><p style="margin-right:3rem;font-family:Quicksand Bold; font-size:15px; color:#3b4956">'+ data[i].creationtime+' </p></div></div>';

                            $(".inner").append(contentString);

                            var image = document.getElementById(i);
                            image.src = "data:image/jpg;base64," + data[i].thumbnail;

                        }
                    });
                }

            }
        )
            .catch(function(err) {
                console.log('Fetch Error', err);
            });
    }
}

$('.on').scroll(function () {
    var top = $('.on').scrollTop();
    $('.two').html("top: "+top+" diff: "+($(".inner").height() - $(".on").height()));
    if (top >= $(".inner").height() - $(".on").height()) {
        $('.two').append(" bottom");
        loadMore(feedCursor);
    }
});

loadMore("");

var loadMoreComments = function(idReport,cursor){
    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    fetch(restRequest("/api/report/comment/get/" + idReport + "?cursor=" + cursor, 'GET', headers, body)).then(function(response) {

            if (response.status === 200 || response.status === 204) {
                commentsCursor = response.headers.get("Cursor");
                response.json().then(function(data) {
                    console.log(data);
                    var i;
                    for(i = 0; i<data.length; i++){
                        $(".inner_comment").append("<p>" + data[i].ReportComment + "</p>");
                    }

                });
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

$('.comments').scroll(function () {
    var top = $('.comments').scrollTop();
    $('.two').html("top: "+top+" diff: "+($(".inner_comment").height() - $(".comments").height()));
    if (top >= $(".inner_comment").height() - $(".comments").height()) {
        $('.two').append("bottom");
        loadMoreComments(reportID,commentsCursor);
    }
});

function getAvailableWorker(cursor){
    if(cursor !== null) {
        var body = "";
        var headers = new Headers();
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));
        fetch(restRequest('/api/org/listworkers?cursor=' + cursor, 'GET', headers, body)).then(function(response) {

                if (response.status === 200) {
                    var newCursor = response.headers.get("Cursor");
                    response.json().then(function(data) {
                        console.log(JSON.stringify(data));
                        if(data !== null){
                            var i;
                            console.log(data.length);
                            for(i = 0; i < data.length; i++){
                                var email = data[i].worker;
                                $(".dropdown-m").append("<option class='pointer-finger'" +
                                    "style='font-family:Quicksand border: 1px rgba(144,148,156,0.51) solid' value=" + JSON.stringify(data[i]) + ">" + email + "</option>");
                                console.log($('email_select').child().last().val());
                            }

                        }else{
                            alert("Esta empresa ainda não tem trabalhadores associados.")
                        }
                    });
                    if(newCursor !== null) {
                        getAvailableWorker(newCursor);
                    }

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

function giveTask(){
    var body = {
        email: JSON.parse($("#email_select").val()).worker,
        report: idReportCurr,
        indications: "Por favor, tenha cuidado com a lenha."
    };
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    fetch(restRequest('/api/org/givetask', 'POST', headers, JSON.stringify(body))).then(function() {
        alert("Tarefa atribuida com sucesso");
    }).catch(function(err) {
        console.log('Fetch Error', err);
    });;

}

