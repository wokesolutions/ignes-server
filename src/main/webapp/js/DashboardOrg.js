var map = null;
var geocoder = new google.maps.Geocoder();
var reports;
var reportID;
var tasks = [];
var commentsCursor;
var tasksCursor;
var email_worker;
var currentfeed = 10;
var current_position = "map_variable";
var idReportCurr;
var numWorkers;
var currentLoc ={
    center: {lat: 38.661148, lng: -9.203075},
    zoom: 18
};

var to_show;
var emailsarr;
var workersarr;
var variable;
var show = false;
var show_view = false;

getCurrentLocation();

var URL_BASE = 'https://mimetic-encoder-209111.appspot.com';

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
    document.getElementById("close_window_worker").onclick = closeWindowWorker;
    document.getElementById("add_task").onclick = giveTask;
    document.getElementById("send_application").onclick = sendApplication;
    document.getElementById("remove_button").onclick = showButtonDelete;
    document.getElementById("view_button").onclick = showButtonView;

    $("#email_select").change(function(){
        var email = $("#email_select").val();
        var index = emailsarr.indexOf(email);
        var worker = workersarr[index];

        var name = worker.name;

        $("#name_worker").html(name);
        $("#email_task").html(email);
    });

    emailsarr = [];
    workersarr = [];

    getFirstWorkers();


}

function getShowMore(){
    hideShow("show_more_variable");
}

function showButtonDelete(){

    if( document.getElementById("show_button_0" ).style.display === "block") {
        for (var i = 0; i < numWorkers; i++)
            document.getElementById("show_button_" + i).style.display = "none";
        show_view = false;
    }

    if(show === false){
        for(var i = 0; i<numWorkers; i++ )
            document.getElementById("delete_button_" + i ).style.display = "block";
        show = true;
    }else {
        for(var i = 0; i<numWorkers; i++ )
            document.getElementById("delete_button_" + i ).style.display = "none";
        show = false;
    }

}

function showButtonView(){

    if( document.getElementById("delete_button_0" ).style.display === "block"){
        for(var i = 0; i<numWorkers; i++ )
            document.getElementById("delete_button_" + i ).style.display = "none";
        show = false;
    }

    if(show_view === false){
        for(var i = 0; i<numWorkers; i++ )
            document.getElementById("show_button_" + i ).style.display = "block";
        show_view = true;
    }else {
        for(var i = 0; i<numWorkers; i++ )
            document.getElementById("show_button_" + i ).style.display = "none";
        show_view = false;
    }

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
}

function getCurrentLocation() {
    var geo = false;
    if(navigator.geolocation) {
        console.log("ola");

        navigator.geolocation.getCurrentPosition(function (position) {
            geo = true;
            console.log("adeus");
            currentLoc = {
                center: {lat: position.coords.latitude, lng: position.coords.longitude},
                zoom: 15
            };

            var mapElement = document.getElementById('map');
            map = new google.maps.Map(mapElement, {center: {lat: position.coords.latitude, lng: position.coords.longitude},
                zoom: 15});

            getMarkers();
        })

    }
    if(!geo){

        var mapElement = document.getElementById('map');
        map = new google.maps.Map(mapElement, currentLoc);
    }

    getMarkers();

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

        if(document.getElementById("open_button").style.display === "block")
            document.getElementById("open_button").style.display = "none";

        else if(document.getElementById("candidate_button").style.display === "block") {
            document.getElementById("candidate_button").style.display = "none";
        }
        else if(document.getElementById("wait_button").style.display === "block")
            document.getElementById("wait_button").style.display = "none";

        else if(document.getElementById("closed_button").style.display === "block")
            document.getElementById("closed_button").style.display = "none";

        else if(document.getElementById("standby_button").style.display === "block")
            document.getElementById("standby_button").style.display = "none";

        document.getElementById("details_report").style.display = "none";

    }else if(current_position === "show_more_users_variable"){

        document.getElementById("profile_workers").style.display = "none";

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

        if(to_show === 0)
            document.getElementById("standby_button").style.display = "block";
        else if(to_show === 1)
            document.getElementById("open_button").style.display = "block";
        else if(to_show === 2)
            document.getElementById("candidate_button").style.display = "block";
        else if(to_show === 3)
            document.getElementById("wait_button").style.display = "block";
        else if(to_show === 4)
            document.getElementById("closed_button").style.display = "block";

        document.getElementById("details_report").style.display = "block";
        current_position = "show_more_variable";

    }else if(element === "show_more_users_variable"){
        document.getElementById("profile_workers").style.display = "block";
        current_position = "show_more_users_variable";

    }

}

function verifyIsLoggedIn(){

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/verifytoken','GET', headers, body)).then(function(response) {

            if (response.status !== 200) {

                window.location.href = "../index.html";

            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });



}

function logOut(){

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

function getMarkers(cursor){
    if(cursor===undefined) cursor = "";

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/org/reports?cursor=' + cursor , 'GET', headers, body)).then(function(response) {
        var newCursor = response.headers.get("Cursor");
            if (response.status === 200) {
                response.json().then(function(data) {
                    reports = data;
                    fillMap(reports, newCursor);
                });

            }else if(response.status === 204){
                var empty=[];
                fillMap(empty, newCursor);
            }
            else{
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
        var lat = reports[i].lat;
        var lng = reports[i].lng;
        var status = reports[i].status;
        var budget = reports[i].budget;
        var marker_color;
        var tasktime = reports[i].tasktime;
        var gravity = reports[i].gravity;
        var color;

        if(gravity === 1) {
            color = '#5dcb21';
            if(status === "standby") {
                marker_color = "../marcadores/g1-standby.png";
            }
            else if(status === "closed")
                marker_color = "../marcadores/g1-closed-mine.png";
            else if(tasktime !== null && tasktime !== undefined){
                tasks.push(reports[i]);
                marker_color = "../marcadores/g1-accepted-mine.png";
            }
            else if(budget !== null && budget !== undefined)
                marker_color = "../marcadores/g1-pending-mine.png";
            else
                marker_color = "../marcadores/g1-open.png";
        }
        else if(gravity === 2) {
            color = '#b9ff2a';
            if(status === "standby")
                marker_color = "../marcadores/g2-standby.png";
            else if(status === "closed")
                marker_color = "../marcadores/g2-closed-mine.png";
            else if(tasktime !== null && tasktime !== undefined){
                tasks.push(reports[i]);
                marker_color = "../marcadores/g2-accepted-mine.png";
            }
            else if(budget !== null && budget !== undefined)
                marker_color = "../marcadores/g2-pending-mine.png";
            else
                marker_color = "../marcadores/g2-open.png";
        }
        else if(gravity === 3) {
            color = '#ffcc31';
            if(status === "standby")
                marker_color = "../marcadores/g3-standby.png";
            else if(status === "closed")
                marker_color = "../marcadores/g3-closed-mine.png";
            else if(tasktime !== null && tasktime !== undefined){
                tasks.push(reports[i]);
                marker_color = "../marcadores/g3-accepted-mine.png";
            }
            else if(budget !== null && budget !== undefined)
                marker_color = "../marcadores/g3-pending-mine.png";
            else
                marker_color = "../marcadores/g3-open.png";
        }
        else if(gravity === 4) {
            color = '#ff7c20';
            if(status === "standby")
                marker_color = "../marcadores/g4-standby.png";
            else if(status === "closed")
                marker_color = "../marcadores/g4-closed-mine.png";
            else if(tasktime !== null && tasktime !== undefined){
                tasks.push(reports[i]);
                marker_color = "../marcadores/g4-accepted-mine.png";
            }
            else if(budget !== null && budget !== undefined)
                marker_color = "../marcadores/g4-pending-mine.png";
            else
                marker_color = "../marcadores/g4-open.png";
        }
        else if(gravity === 5) {
            color = '#bc0f0f';
            if(status === "standby")
                marker_color = "../marcadores/g5-standby.png";
            else if(status === "closed")
                marker_color = "../marcadores/g5-closed-mine.png";
            else if(tasktime !== null && tasktime !== undefined){
                tasks.push(reports[i]);
                marker_color = "../marcadores/g5-accepted-mine.png";
            }
            else if(budget !== null && budget !== undefined)
                marker_color = "../marcadores/g5-pending-mine.png";
            else
                marker_color = "../marcadores/g5-open.png";
        }
        else{
            console.log("Não existe gravidade neste reporte");
        }
        if(reports[i].points === null || reports[i].points === undefined) {
            marker = new google.maps.Marker({
                position: new google.maps.LatLng(lat, lng),
                map: map,
                icon: marker_color
            });
        } else{
            marker = new google.maps.Polygon({
                paths: reports[i].points,
                strokeColor: color,
                strokeOpacity: 0.8,
                strokeWeight: 2,
                fillColor: color,
                fillOpacity: 0.35
            });
            marker.setMap(map);

            marker = new google.maps.Marker({
                position: new google.maps.LatLng(lat, lng),
                map: map,
                icon: marker_color
            });
        }


        google.maps.event.addListener(marker, 'click', (function(marker, i) {

            return function() {
                if(reports[i].status === "closed")
                    to_show = 4;
                else if(reports[i].status === "standby")
                    to_show = 0;
                else if(reports[i].tasktime !== null && reports[i].tasktime !== undefined)
                    to_show = 1;
                else if(reports[i].budget !== null && reports[i].budget !== undefined) {
                    document.getElementById('budget_input').innerHTML= reports[i].budget;
                    if(reports[i].info !== null && reports[i].info !== undefined && reports[i].info != "")
                        document.getElementById('info_input').innerHTML= reports[i].info;
                    else
                        document.getElementById('info_input').innerHTML= "-";
                    to_show = 3;
                }
                else
                    to_show= 2;




                reportID = reports[i].report;
                $(".remove_work").remove();
                getInfo(reportID, i);

            }
        })(marker, i));
    }

    if(cursor !== null){
        console.log(cursor);
        getMarkers(cursor);
    }else{
        console.log("iniciar");
        loadMore();
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

                document.getElementById("show_title").style.display = "block";
                document.getElementById("show_address").style.display = "block";
                document.getElementById("show_state").style.display = "block";
                document.getElementById("show_gravity").style.display = "block";
                document.getElementById("show_more_button").style.display = "block";

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

                if(reports[i].description !== "" && reports[i].description !== undefined )
                    document.getElementById('report_description_id').innerHTML= reports[i].description;
                else
                    document.getElementById('report_description_id').innerHTML= "-";

                var status = "";
                if(reports[i].status === "open")
                    status = "Aberto";
                else if(reports[i].status === "closed")
                    status = "Fechado";
                else if (reports[i].status === "wip")
                    status = "Em resolução";
                else if(reports[i].status === "wip")
                    status = "Em espera";

                document.getElementById('report_state_id').innerHTML= status;
                document.getElementById('report_state_id_2').innerHTML= status;
                document.getElementById('report_gravity_id').innerHTML= reports[i].gravity;
                document.getElementById('report_gravity_id_2').innerHTML= reports[i].gravity;

                var categoria = translate(reports[i].category);
                document.getElementById('category_report').innerHTML= categoria;


                var content= "";
                var workers_feed = reports[i].workers;
                if (workers_feed !== undefined) {
                    var j;
                    for (j = 0; j < workers_feed.length; j++) {
                        content += '<div class="remove_work"><p class="info_text_response" style="font-family: Quicksand Bold">' + workers_feed[j] + '</p></div>';
                    }
                } else {
                    content += '<div class="remove_work"><p class="info_text_response" style="font-family: Quicksand Bold">Não há trabalhadores associados a este reporte.</p></div>'
                }
                $(".list_users_report").append(content);
                if(reports[i].isprivate === true)
                    document.getElementById('report_private_id').innerHTML= "Privado";
                else
                    document.getElementById('report_private_id').innerHTML= "Público";

                document.getElementById('report_comments_id').innerHTML= reports[i].comments;
                document.getElementById('report_user_id').innerHTML= reports[i].username;
                document.getElementById('report_creationtime_id').innerHTML= reports[i].creationtime;
                document.getElementById('report_up_id').innerHTML= reports[i].ups;
                document.getElementById('report_down_id').innerHTML= reports[i].downs;

                $(".comments_remove").remove();
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

function translate(category){
    var cat= "";
    switch (category) {
        case "LIXO":
            cat = "Limpeza de Lixo Geral";
            break;
        case "PESADOS":
            cat = "Transportes Pesados";
            break;
        case "PERIGOSOS":
            cat = "Transportes Perigosos";
            break;
        case "PESSOAS":
            cat = "Transportes de Pessoas";
            break;
        case "TRANSPORTE":
            cat = "Transportes Gerais";
            break;
        case "MADEIRAS":
            cat = "Madeiras";
            break;
        case "CARCACAS":
            cat = "Carcaças";
            break;
        case "BIOLOGICO":
            cat = "Outros resíduos biológicos";
            break;
        case "JARDINAGEM":
            cat = "Jardinagem";
            break;
        case "MATAS":
            cat = "Limpeza de Matas/Florestas";

    }
    return cat;
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

function closeWindowWorker(){
    hideShow("users_variable");
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
                getFirstWorkers();
                showWorkers();
                document.getElementById("worker_username").innerHTML = "";
                document.getElementById("worker_email").innerHTML = "";
                document.getElementById("worker_jobs").innerHTML = "";

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
                    if(data != null){
                        var i;
                        for(i = 0; i < data.length; i++){
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            var cell4 = row.insertCell(3);
                            var cell5 = row.insertCell(4);
                            cell1.innerHTML = data[i].name;
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].job;
                            cell4.outerHTML= "<button id='delete_button_"+ i +"'style='display:none' type='submit' class='btn-circle btn-primary-style' onclick='deleteWorker(this.parentNode.rowIndex)'><a class='fa fa-trash-o'></a></button>";
                            cell5.outerHTML= "<button id='show_button_"+ i +"'style='display:none' type='submit' class='btn-circle btn-primary-style' onclick='viewWorkers(this.parentNode.rowIndex)'><a class='fa fa-search'></a></button>";

                        }
                        numWorkers= data.length;
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
                    if(data != null){
                        var i;

                        for(i = 0; i < data.length; i++){
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            var cell4 = row.insertCell(3);
                            var cell5 = row.insertCell(4);
                            cell1.innerHTML = data[i].name;
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].job;
                            cell4.outerHTML= "<button  id='delete_button_"+ i +"'style='display:none' type='submit' class='btn-circle btn-primary-style' onclick='deleteWorker(this.parentNode.rowIndex)'><a class='fa fa-trash-o'></a></button>";
                            cell5.outerHTML= "<button id='show_button_"+ i +"'style='display:none' type='submit' class='btn-circle btn-primary-style' onclick='viewWorkers(this.parentNode.rowIndex)'><a class='fa fa-search'></a></button>";

                        }
                        numWorkers= data.length;
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

                        if(data != null){
                            var i;
                            for(i = 0; i < data.length; i++){
                                var row = table.insertRow(-1);
                                var cell1 = row.insertCell(0);
                                var cell2 = row.insertCell(1);
                                var cell3 = row.insertCell(2);
                                var cell4 = row.insertCell(3);
                                var cell5 = row.insertCell(4);
                                cell1.innerHTML = data[i].name;
                                cell2.innerHTML = data[i].email;
                                cell3.innerHTML = data[i].job;
                                cell4.outerHTML= "<button  id='delete_button_"+ i +"'style='display:none' type='submit' class='btn-circle btn-primary-style' onclick='deleteWorker(this.parentNode.rowIndex)'><a class='fa fa-trash-o'></a></button>";
                                cell5.outerHTML= "<button id='show_button_"+ i +"'style='display:none' type='submit' class='btn-circle btn-primary-style' onclick='viewWorkers(this.parentNode.rowIndex)'><a class='fa fa-search'></a></button>";
                            }
                            numWorkers= data.length;
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
                    document.getElementById("organization_nif").innerHTML = data.nif;
                    document.getElementById("organization_email").innerHTML = data.email;
                    document.getElementById("organization_addresses").innerHTML = data.address;
                    document.getElementById("organization_locality").innerHTML = data.locality;
                    document.getElementById("organization_zip").innerHTML = data.zip;
                    var show_service ="";
                    var service = JSON.parse(data.services);

                    for(var i = 0; i< service.length; i++) {
                        if (i !== service.length - 1) {
                            var service_temp= translate(service[i]);

                            show_service += service_temp + "/";
                        }
                        else {
                            var service_temp= translate(service[i]);
                            show_service += service_temp;
                        }
                    }
                    document.getElementById("organization_service").innerHTML = show_service;
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

    var delWorker = prompt("Por favor indique o motivo:", "Escreva o motivo...");
    if(delWorker != null) {
        var body = JSON.stringify({
            info:delWorker
        });
        var headers = new Headers();
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));

        fetch(restRequest('/api/org/deleteworker/' + email, 'DELETE', headers, body)).then(function (response) {

                if (response.status === 200 || response.status === 204) {
                    alert("Trabalhador apagado com sucesso.")

                } else {
                    alert("Falha ao apagar o utilizador.")
                }

            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }else
        alert("Indique um motivo.")
}

function sendApplication(){
    var info = document.getElementById("info_application").value;
    var budget = document.getElementById("budget_application").value;

    var body = {
        info: info,
        budget: budget
    };
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    fetch(restRequest('/api/org/apply/' + idReportCurr, 'POST', headers, JSON.stringify(body))).then(function(response) {
        if(response.status === 200)
            alert("Candidatura enviada com sucesso");
        else
            alert("Candidatura falhou ao enviar")
    }).catch(function(err) {
        console.log('Fetch Error', err);
    });;
}

var loadMore = function () {
    var i;
    for (i = currentfeed - 10; i < currentfeed; i++) {
        if (tasks[i] === null || tasks[i] === undefined)
            break;

        var status = "";
        if(tasks[i].status === "open")
            status = "Aberto";
        else if(tasks[i].status === "closed")
            status = "Fechado";
        else if (tasks[i].status === "wip")
            status = "Em resolução";
        else if(tasks[i].status === "wip")
            status = "Em espera";

        var category = translate(tasks[i].category);

        var workers_feed = tasks[i].workers;
        var contentString = '<div id="content" style="margin-bottom:2rem; background:#f8f9fa;">' +
            '<div class="row" >' +
            '<div class="col-lg-3 col-md-3 mx-auto">' +
            '<div class="row">' +
            '<div class="col-lg-1">' +
            '<p class="text-center"style="margin-top:3px; margin-left:1rem;font-family:Quicksand Bold; font-size:15px; color:#AD363B"">' + tasks[i].gravity +
            '</div>' +
            '<div class="col-lg-1">' +
            '<i class="fa fa-tachometer" style="color: #AD363B"></i>' +
            '</div>' +
            '<div class="col-lg-10"></div>' +
            '</div></div>' +
            '<div class="col-lg-6 col-md-6 mx-auto text-center" style="margin-top:1rem">' +
            '<i class="fa fa-map-marker" style="color:#AD363B; font-size: 2rem"> </i>' +
            '</div>' +
            '<div class="col-lg-3 col-md-3 mx-auto-"><p class="text-center"style="font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + status + '</p></div>' +
            '</div>' +
            ' <div class="row" >' + '<div class="col-lg-12 col-md-12 mx-auto text-center">' +
            '<p style="margin-bottom:0;font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + tasks[i].address + '</p>' + '</div>' + '</div><hr>' +

            '<div class="row">' +
            '<div class="col-lg-12 text-center">' +
            '<p style="font-family:Quicksand Bold; font-size: 15px; color:#AD363B">' + tasks[i].title + '</p>' +
            '</div>' +
            '</div>' +
            '<div class="row">' +
            '<div class="col-lg-12 text-center">' +
            '<p class="info_text_response text-center" style="font-family:Quicksand; margin-left:1.5rem">' + category + '</p>' +
            '</div>' +
            '</div>' +

            '<div class="row">' +
            '<div class="col-lg-6 text-center">' +
                '<div class="row" >' +
                '<div class="col-lg-8 col-md-8 text-right ">' +
                '<div class="row" >' +
                '<div class="col-lg-4 col-md-4  ">' +
                '<img class="img_user" src="../images/avatar.png" height="20" width="20"/>' +
                '</div>' +
                '<div class="col-lg-4 col-md-4 text-left ">' +
                '<p class="info_text_response" style="font-family: Quicksand Bold">' + tasks[i].username + '</p>' +
                '</div>' +
                '<div class="col-lg-4 col-md-4 text-left">' +
                '</div>' +
                '</div>' +
                '</div>' +
                '<div class="col-lg-4 col-md-4 text-left"></div>' +
                '</div>' +
                '<div class="col-lg-12 text-center">' +
                '<img style="height:10rem; margin-bottom:1rem"id=' + i + '>' +
                '</div>' +
            '</div>' +
            '<div class="col-lg-6">' +
                '<div class="row">' +
                '<div class="col-lg-12 mx-lg-auto text-center">' +
                '<p class="info_text_response text-center" style="font-family: Quicksand Bold; color:#AD363B" >Trabalhadores associados:</p>';
            contentString += '</div>' +
                '</div>' +
                '<div class="row">' +
                '<div class="col-lg-12 mx-lg-auto text-center">';
        if (workers_feed !== undefined) {
            var j;
            for (j = 0; j < workers_feed.length; j++) {
                contentString += '<p class="info_text_response" style="font-family: Quicksand Bold">' + workers_feed[j] + '</p>';
            }
        } else {
            contentString += '<p class="info_text_response" style="font-family: Quicksand Bold">Não há trabalhadores associados a este reporte.</p>'
        }

        contentString +='</div>' +
            '</div></div>' +
            '</div>' +
            '<hr style="margin-bottom: 0; margin-top:0">' +
            '<div class="row">' +
            '<div class="col-lg-6 text-left"></div>' +
            '<div class="col-lg-6 text-right">' +
            '<p style="margin-right:3rem;font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + tasks[i].creationtime + ' </p>' +
            '</div>' +
            '</div>' +
            '</div></div>';


        $(".inner").append(contentString);
        var image = document.getElementById(i);
        image.src = "data:image/jpg;base64," + tasks[i].thumbnail;


    }
    currentfeed += 10;
}

$('.on').scroll(function () {
    var top = $('.on').scrollTop();
    if (top >= $(".inner").height() - $(".on").height()) {
        $('.two').append(" bottom");
        loadMore();
    }
});

var loadMoreComments = function(idReport,cursor){
    if(cursor!= null) {
        var body = "";
        var headers = new Headers();
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));
        fetch(restRequest("/api/report/comment/get/" + idReport + "?cursor=" + cursor, 'GET', headers, body)).then(function (response) {

                if (response.status === 200 || response.status === 204) {
                    commentsCursor = response.headers.get("Cursor");
                    response.json().then(function (data) {
                        var i;

                        for (i = 0; i < data.length; i++) {
                            $(".inner_comment").append('<div class="comments_remove" >'+
                                '<div id="content" style="margin-bottom:1rem; background:#f8f9fa; width:300px">' +
                                '<div class="row">' +
                                     '<div class="col-lg-12 text-left">' +
                                         '<p style="font-family:Quicksand Bold; color:#AD363B; margin-right:1rem; font-size:15px;">' + data[i].username + '</p></div></div>' +
                                '<div class="row"><div class="col-lg-12 text-left">' +
                                        '<p style="font-family:Quicksand; font-size:14px;">' + data[i].text + '</p>' +
                                    '</div>' +
                                '</div>' +
                                '<hr style="margin-top:0;">' +
                                '<div class="row">' +
                                '<div class="col-lg-6"></div>' +
                                '<div class="col-lg-6 text-right">' +
                                '<p style="font-family:Quicksand Bold; font-size:12px; margin-bottom:0;">' + data[i].creationtime +
                                '</p></div></div></div></div>');
                        }

                    });
                }

            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }
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

                        if(data !== null){
                            var i;
                            $(".dropdown-m").append('<option value="" disabled selected>Selecione o trabalhador</option>');
                            for(i = 0; i < data.length; i++){

                                var email = data[i].email;


                                $(".dropdown-m").append("<option class='pointer-finger'" +
                                    "style='font-family:Quicksand border: 1px rgba(144,148,156,0.51) solid' value=" + email + ">" + email + "</option>");


                                emailsarr.push(email);
                                workersarr.push(data[i]);
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
    console.log($("#input_ind").val());
    var body = {
        email: $("#email_select").val(),
        report: idReportCurr,
        indications: $("#input_ind").val()
    };
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    fetch(restRequest('/api/org/givetask', 'POST', headers, JSON.stringify(body))).then(function(response) {
        if(response.status === 200){
            alert("Tarefa atribuida com sucesso");
        }else
            alert("Falha a atribuir tarefa");

    }).catch(function(err) {
        console.log('Fetch Error', err);
    });;
}

function viewWorkers(row){
    email_worker = document.getElementById("user_table").rows[row].cells[1].innerHTML;
    var name = document.getElementById("user_table").rows[row].cells[0].innerHTML;
    var service = document.getElementById("user_table").rows[row].cells[2].innerHTML;

    document.getElementById("worker_email_id").innerHTML = email_worker;
    document.getElementById("worker_name").innerHTML = name;
    document.getElementById("worker_services").innerHTML = service;

    hideShow("show_more_users_variable");
    $(".tasks_remove").remove();
    loadMoreTasks(email_worker, "");
}

var loadMoreTasks = function(email,cursor){

    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    fetch(restRequest("/api/worker/tasks/" + email + "?cursor=" + cursor, 'GET', headers, body)).then(function(response) {

            if (response.status === 200 || response.status === 204) {
                tasksCursor = response.headers.get("Cursor");
                response.json().then(function(data) {

                    document.getElementById("num_report").innerHTML = data.length;
                    var i;
                    for(i = 0; i<data.length; i++){

                        var type_private = "";
                        var description = "-";
                        var indications = "-";
                        var status = "";

                        if(data[i].isprivate === true)
                            type_private= "Privado";
                        else
                            type_private = "Público";

                        if(data[i].description !== undefined)
                            description = data[i].description;

                        if(data[i].indications !== undefined)
                            indications= data[i].indications;

                        if(data[i].status === "open")
                            status = "Aberto";
                        else if(data[i].status === "closed")
                            status = "Fechado";
                        else if (data[i].status === "wip")
                            status = "Em resolução";
                        else if(data[i].status === "wip")
                            status = "Em espera";

                        var category = translate(data[i].category);
                        var id_img = "img_"+ i;
                        var contentString = '<div class="tasks_remove" >'+
                            '<div id="content" style=" margin-bottom:1rem; background:#f8f9fa;">' +
                            '<div class="row" >' +
                            '<div class="col-lg-3 col-md-3 mx-auto">' +
                            '<div class="row">' +
                            '<div class="col-lg-1">' +
                            '<p class="text-center"style="margin-top:3px; margin-left:1rem;font-family:Quicksand Bold; font-size:15px; color:#AD363B"">' + data[i].gravity +
                            '</div>' +
                            '<div class="col-lg-1">' +
                            '<i class="fa fa-tachometer" style="color: #AD363B"></i>' +
                            '</div>' +
                            '<div class="col-lg-10"></div>' +
                            '</div></div>' +
                            '<div class="col-lg-6 col-md-6 mx-auto text-center" style="margin-top:1rem">' +
                            '<i class="fa fa-map-marker" style="color:#AD363B; font-size: 2rem"> </i>' +
                            '</div>' +
                            '<div class="col-lg-3 col-md-3 mx-auto-"><p class="text-center"style="font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + status + '</p></div>' +
                            '</div>' +
                            ' <div class="row" >' + '<div class="col-lg-12 col-md-12 mx-auto text-center">' +
                            '<p style="margin-bottom:0;font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + data[i].address + '</p>' + '</div>' + '</div><hr style="margin-bottom: 0; margin-top:0">' +

                            '<div class="row">' +
                            '<div class="col-lg-12 text-center">' +
                            '<p style="font-family:Quicksand Bold; font-size: 15px; color:#AD363B">' + data[i].title + '</p>' +
                            '</div>' +
                            '</div>' +

                            '<div class="row">' +
                            '<div class="col-lg-12 text-center">' +
                            '<p class="info_text_response text-center" style="font-family:Quicksand; margin-left:1.5rem">' + category + '</p>' +
                            '</div>' +
                            '</div>' +

                            '<div class="row">' +
                            '<div class="col-lg-6 text-center">' +
                            '<div class="row" >' +
                            '<div class="col-lg-8 col-md-8 text-right ">' +
                            '<div class="row" >' +
                            '<div class="col-lg-4 col-md-4  ">' +
                            '<i class="fa fa-lock"></i>' +
                            '</div>' +
                            '<div class="col-lg-6 col-md-6 text-left ">' +
                            '<p class="info_text_response" style="font-family: Quicksand Bold">' + type_private + '</p>' +
                            '</div>' +
                            '<div class="col-lg-2 col-md-2 text-left">' +
                            '</div>' +
                            '</div>' +
                            '</div>' +
                            '<div class="col-lg-4 col-md-4 text-left"></div>' +
                            '</div>' +
                            '<div class="col-lg-12 text-center">' +
                            '<img style="height:10rem; margin-bottom:1rem"id="'+id_img +'">' +
                            '</div>' +
                            '</div>' +
                            '<div class="col-lg-6">' +
                            '<div class="row">' +
                            '<div class="col-lg-12 mx-lg-auto text-center" style="margin-top:2.5rem">' +
                            '<p class="info_text_response text-center" style="font-family: Quicksand Bold; color:#AD363B" >Descrição</p>'+
                            '<p class="info_text_response text-center" >'+description+'</p>';
                        contentString += '</div>' +
                            '</div>' +
                            '<div class="row">' +
                            '<div class="col-lg-12 mx-lg-auto text-center">'+
                            '<p class="info_text_response text-center" style="font-family: Quicksand Bold; color:#AD363B" >Indicações</p>'+
                            '<p class="info_text_response text-center" >'+indications+'</p>';

                        contentString +='</div>' +
                            '</div></div>' +
                            '</div>' +
                            '<hr style="margin-bottom: 0; margin-top:0">' +
                            '<div class="row">' +
                            '<div class="col-lg-6 text-left"></div>' +
                            '<div class="col-lg-6 text-right">' +
                            '<p style="margin-right:3rem;font-family:Quicksand Bold; font-size:15px; color:#3b4956">' + data[i].creationtime + ' </p>' +
                            '</div>' +
                            '</div>' +
                            '</div>';


                        $(".tasks_worker").append(contentString);

                       getThumbnailTask(data[i].task, i);
                    }

                });
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

$('.tasks').scroll(function () {
    var top = $('.tasks').scrollTop();
    if (top >= $(".tasks_worker").height() - $(".tasks").height()) {
        $('.two').append("bottom");
        loadMoreComments(email_worker,tasksCursor);
    }
});

function getThumbnailTask(reportId, i){
    var body = "";
    var headers = new Headers();
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    fetch(restRequest('/api/report/thumbnail/' + reportId, 'GET', headers, body)).then(function(response) {
        if(response.status === 200){
            response.json().then(function(data) {
                var img = document.getElementById("img_" + i);
                img.src = "data:image/jpg;base64," + data.thumbnail;
            });
        } else{
            console.log("Não deu 200 ao pedir o thumbnail");
        }
    }).catch(function(err) {
        console.log('Fetch Error', err);
    }
    );
}

