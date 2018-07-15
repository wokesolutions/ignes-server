var cursor_next;
var cursor_pre;
var cursor_current;
var cursor_next_pending;
var cursor_current_pending;
var cursor_pre_pending;
var cursor_next_pendingrep;
var cursor_current_pendingrep;
var cursor_pre_pendingrep;
var cursor_next_public;
var cursor_current_public;
var cursor_pre_public;
var standby_rep= [];
var public_reports = [];

var user_cursors = [""];
var index_user;

var reports_cursors = [""];
var index_reports;

var pending_cursors = [""];
var index_pending;

var public_cursors = [""];
var index_public;

var current_position = "list_users_variable";

var URL_BASE = 'https://mimetic-encoder-209111.appspot.com';

init();

function init() {
    verifyIsLoggedIn();

    google.charts.load('current', {'packages':['corechart', 'bar']});
    google.charts.setOnLoadCallback(drawPieChart);
    google.charts.setOnLoadCallback(drawGeoChart);
    google.charts.setOnLoadCallback(monthStat);

    getFirstUsers();
    getPendingFirst();
    getPendingReportsFirst();
    getPublicFirst();
    getTopUsers();

    document.getElementById("logout_button").onclick = logOut;
    document.getElementById("public_reports_button").onclick = showPublicReports;
    document.getElementById("add_admin_button").onclick = showAddAdmin;
    document.getElementById("add_adm").onclick = addAdmin;
    document.getElementById("statistics_button").onclick = showStatisticsReports;
    document.getElementById("pending_verify_reports_button").onclick = showPendingReports;
    document.getElementById("next_list").onclick = getNextUsers;
    document.getElementById("previous_list").onclick = getPreUsers;
    document.getElementById("refresh_users").onclick = getFirstUsers;
    document.getElementById("next_list_pending").onclick = getPendingNext;
    document.getElementById("previous_list_pending").onclick = getPendingPre;
    document.getElementById("refresh_orgs_pending").onclick = getPendingFirst;
    document.getElementById("list_pending_button").onclick = showPending;
    document.getElementById("list_users_button").onclick = showUsers;
    document.getElementById("next_reports_pending").onclick = getPendingReportsNext;
    document.getElementById("previous_reports_pending").onclick = getPendingReportsPre;
    document.getElementById("refresh_reports_pending").onclick = getPendingReportsFirst;
    document.getElementById("next_public_reports_pending").onclick = getPublicNext;
    document.getElementById("previous_public_reports_pending").onclick = getPublicPre;
    document.getElementById("refresh_public_reports_pending").onclick = getPublicFirst;

}

function showPendingReports(){
    hideShow("reports_pending_variable");
}

function showPublicReports(){
    hideShow("list_public_variable");
}

function showStatisticsReports(){
    hideShow("statistics_variable");
}

function showUsers(){
    hideShow("list_users_variable");
}

function showPending(){
    hideShow("show_pending_variable");
}

function showAddAdmin(){
    hideShow("add_admin_variable");
}

function hideShow(element){

    if(current_position === "list_users_variable"){

        document.getElementById("list_users").style.display = "none";

    } else if(current_position === "show_pending_variable"){

        document.getElementById("list_pending_orgs").style.display = "none";

    } else if(current_position === "list_public_variable"){

        document.getElementById("list_pending_public_reports").style.display = "none";

    } else if(current_position === "statistics_variable"){

        document.getElementById("statistic").style.display = "none";

    } else if(current_position === "reports_pending_variable"){

        document.getElementById("list_pending_reports").style.display = "none";

    } else if(current_position === "add_admin_variable"){

        document.getElementById("add_admin_report").style.display = "none";

    }

    if(element === "show_pending_variable"){

        document.getElementById("list_pending_orgs").style.display = "block";
        current_position = "show_pending_variable";

    }else if(element === "list_users_variable"){

        document.getElementById("list_users").style.display = "block";
        current_position = "list_users_variable";

    } else if(element === "list_public_variable"){

        document.getElementById("list_pending_public_reports").style.display = "block";
        current_position = "list_public_variable";

    } else if(element === "statistics_variable"){

        document.getElementById("statistic").style.display = "block";
        current_position = "statistics_variable";


    } else if(element === "reports_pending_variable"){

        document.getElementById("list_pending_reports").style.display = "block";
        current_position = "reports_pending_variable";

    } else if(element === "add_admin_variable"){
        document.getElementById("add_admin_report").style.display = "block";
        current_position = "add_admin_variable";
    }
}

function addAdmin(){
    var name = document.getElementById("admin_username").value;
    var email = document.getElementById("admin_email").value;
    var password = document.getElementById("admin_password").value;
    var confirmation = document.getElementById("admin_confirmation").value;
    var locality = document.getElementById("admin_locality").value;
    if(password === confirmation){
        var headers = new Headers();
        var body = {username:name,email:email,locality:locality,password:password};
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));

        fetch(restRequest('/api/admin/register', 'POST', headers, JSON.stringify(body))).then(function(response) {

                if (response.status === 200) {
                    alert("Administrador registado com sucesso.");
                    document.getElementById("admin_username").innerHTML = "";
                    document.getElementById("admin_email").innerHTML = "";
                    document.getElementById("admin_password").innerHTML = "";
                    document.getElementById("admin_confirmation").innerHTML = "";
                    document.getElementById("admin_locality").innerHTML = "";
                }else{
                    alert("Utilizador já existe ou falta informação em algum campo.")
                }

            }
        )
            .catch(function(err) {
                console.log('Fetch Error', err);
            });
    }else{
        alert("A password e a confirmação não estão coerentes.");
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


function getFirstUsers(){
    user_cursors = [""];
    console.log(user_cursors);
    console.log(cursor_pre);
    console.log(cursor_current);
    console.log(cursor_next);
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/userlist?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                index_user = 0;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                document.getElementById("previous_list").style.display = "none";
                if(response.headers.get("Cursor") !== null) {
                    user_cursors.push(response.headers.get("Cursor"));
                    cursor_pre = "";
                    cursor_current = "";
                    cursor_next = response.headers.get("Cursor");
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
                            var cell5 = row.insertCell(4);
                            cell1.outerHTML = "<button type = 'submit' onclick = getProfile(this.parentNode.rowIndex)>" +data[i].username + "</button>";
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].level;
                            if(data[i].org !== undefined)
                                cell4.innerHTML = data[i].org;
                            else
                                cell4.innerHTML = "-";
                            if(data[i].points !== undefined)
                                cell5.innerHTML = data[i].points;
                            else
                                cell5.innerHTML = "-";
                        }

                    }else{
                        alert("Não deu 200.")
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

function getNextUsers(){
    console.log(user_cursors);
    console.log(cursor_pre);
    console.log(cursor_current);
    console.log(cursor_next);
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/userlist?cursor=' + cursor_next,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                index_user++;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                document.getElementById("previous_list").style.display = "block";

                if(response.headers.get("Cursor") !== null) {
                    user_cursors.push(response.headers.get("Cursor"));
                    cursor_pre = cursor_current;
                    cursor_current = cursor_next;
                    cursor_next = response.headers.get("Cursor");

                    if(document.getElementById("next_list").style.display === "none")
                        document.getElementById("next_list").style.display = "block";

                } else{
                    cursor_pre = cursor_current;
                    cursor_current = cursor_next;
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
                            var cell5 = row.insertCell(4);
                            cell1.outerHTML = "<button type = 'submit' onclick = getProfile(this.parentNode.rowIndex)>" +data[i].username + "</button>";
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].level;
                            if(data[i].org !== undefined)
                                cell4.innerHTML = data[i].org;
                            else
                                cell4.innerHTML = "-";
                            if(data[i].points !== undefined)
                                cell5.innerHTML = data[i].points;
                            else
                                cell5.innerHTML = "-";
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

function getPreUsers(){
    console.log(user_cursors);
    console.log(cursor_pre);
    console.log(cursor_current);
    console.log(cursor_next);

    if(index_user - 1 === 0) getFirstUsers();

    else {

        var headers = new Headers();
        var body = "";
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));


        fetch(restRequest('/api/admin/userlist?cursor=' + cursor_pre, 'GET', headers, body)).then(function (response) {
                var table = document.getElementById("user_table");

                if (response.status === 200) {
                    index_user--;
                    if (table.rows.length > 1) {
                        table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                    }
                    document.getElementById("previous_list").style.display = "block";
                    if (response.headers.get("Cursor") !== null) {

                        cursor_next = cursor_current;
                        cursor_current = cursor_pre;
                        cursor_pre = user_cursors[index_user - 1];

                        if (document.getElementById("next_list").style.display === "none")
                            document.getElementById("next_list").style.display = "block";

                    } else {
                        if (document.getElementById("next_list").style.display === "block")
                            document.getElementById("next_list").style.display = "none";
                    }
                    response.json().then(function (data) {
                        console.log(JSON.stringify(data));
                        if (data != null) {
                            var i;
                            for (i = 0; i < data.length; i++) {
                                var row = table.insertRow(-1);
                                var cell1 = row.insertCell(0);
                                var cell2 = row.insertCell(1);
                                var cell3 = row.insertCell(2);
                                var cell4 = row.insertCell(3);
                                var cell5 = row.insertCell(4);
                                cell1.outerHTML = "<button type = 'submit' onclick = getProfile(this.parentNode.rowIndex)>" +data[i].username + "</button>";
                                cell2.innerHTML = data[i].email;
                                cell3.innerHTML = data[i].level;
                                if (data[i].org !== undefined)
                                    cell4.innerHTML = data[i].org;
                                else
                                    cell4.innerHTML = "-";
                                if (data[i].points !== undefined)
                                    cell5.innerHTML = data[i].points;
                                else
                                    cell5.innerHTML = "-";
                            }

                        } else {
                            alert("Esta empresa ainda não tem trabalhadores associados.")
                        }
                    });

                } else {
                    console.log("Tratar do Forbidden");
                }


            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }
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

function getPendingNext(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/orgstoconfirm?cursor=' + cursor_next_pending,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                index_pending++;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                document.getElementById("previous_list_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {
                    pending_cursors.push(response.headers.get("Cursor"));
                    cursor_pre_pending = cursor_current_pending;
                    cursor_current_pending = cursor_next_pending;
                    cursor_next_pending = response.headers.get("Cursor");

                    if(document.getElementById("next_list_pending").style.display === "none")
                        document.getElementById("next_list_pending").style.display = "block";

                } else{
                    cursor_pre_pending = cursor_current_pending;
                    cursor_current_pending = cursor_next_pending;
                    if(document.getElementById("next_list_pending").style.display === "block")
                        document.getElementById("next_list_pending").style.display = "none";
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
                            var cell5 = row.insertCell(4);
                            var cell6 = row.insertCell(5);
                            var cell7 = row.insertCell(6);
                            var cell8 = row.insertCell(7);
                            var cell9 = row.insertCell(8);
                            var cell10 = row.insertCell(9);
                            cell1.innerHTML = data[i].nif;
                            cell2.innerHTML = data[i].name;
                            cell3.innerHTML = data[i].email;
                            cell4.innerHTML = data[i].address;
                            cell5.innerHTML = data[i].locality;
                            cell6.innerHTML = data[i].phone;
                            var service = JSON.parse(data[i].services);
                            var show_service ="";

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

                            cell7.innerHTML = show_service;
                            cell8.innerHTML = data[i].creationtime;

                            var type= "";

                            if(data[i].isfirestation)
                                type= "Privada";
                            else
                                type= "Pública";

                            cell9.innerHTML = data[i].isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";
                        }

                    }else{
                        alert("Não deu 200.")
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

function getPendingPre(){
    if(index_pending- 1 === 0) getPendingFirst();
    else {
        var headers = new Headers();
        var body = "";
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));


        fetch(restRequest('/api/admin/orgstoconfirm?cursor=' + cursor_pre_pending, 'GET', headers, body)).then(function (response) {
                var table = document.getElementById("orgs_pending_table");

                if (response.status === 200) {
                    index_pending--;
                    if (table.rows.length > 1) {
                        table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                    }

                    document.getElementById("previous_list_pending").style.display = "block";

                    if (response.headers.get("Cursor") !== null) {

                        cursor_next_pending = cursor_current_pending;
                        cursor_current_pending = cursor_pre_pending;
                        cursor_pre_pending = pending_cursors[index_pending - 1];

                        if (document.getElementById("next_list_pending").style.display === "none")
                            document.getElementById("next_list_pending").style.display = "block";

                    } else {
                        if (document.getElementById("next_list_pending").style.display === "block")
                            document.getElementById("next_list_pending").style.display = "none";
                    }
                    response.json().then(function (data) {
                        console.log(JSON.stringify(data));
                        if (data != null) {
                            var i;
                            for (i = 0; i < data.length; i++) {
                                var row = table.insertRow(-1);
                                var cell1 = row.insertCell(0);
                                var cell2 = row.insertCell(1);
                                var cell3 = row.insertCell(2);
                                var cell4 = row.insertCell(3);
                                var cell5 = row.insertCell(4);
                                var cell6 = row.insertCell(5);
                                var cell7 = row.insertCell(6);
                                var cell8 = row.insertCell(7);
                                var cell9 = row.insertCell(8);
                                var cell10 = row.insertCell(9);
                                cell1.outerHTML = data[i].nif;
                                cell2.innerHTML = data[i].name;
                                cell3.innerHTML = data[i].email;
                                cell4.innerHTML = data[i].address;
                                cell5.innerHTML = data[i].locality;
                                cell6.innerHTML = data[i].phone;
                                cell7.innerHTML = data[i].services;
                                cell8.innerHTML = data[i].creationtime;
                                cell9.innerHTML = data[i].isfirestation;
                                cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";
                            }

                        } else {
                            alert("Não deu 200.")
                        }
                    });

                } else {
                    console.log("Tratar do Forbidden");
                }


            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }
}

function getPendingFirst(){
    pending_cursors = [""];
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/orgstoconfirm?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                index_pending = 0;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(response.headers.get("Cursor") !== null) {
                    pending_cursors.push(response.headers.get("Cursor"));
                    cursor_pre_pending = "";
                    cursor_current_pending = "";
                    cursor_next_pending = response.headers.get("Cursor");

                    if(document.getElementById("next_list_pending").style.display === "none")
                        document.getElementById("next_list_pending").style.display = "block";
                    if(document.getElementById("previous_list_pending").style.display === "block")
                        document.getElementById("previous_list_pending").style.display = "none";
                } else{
                    if(document.getElementById("next_list_pending").style.display === "block")
                        document.getElementById("next_list_pending").style.display = "none";
                    if(document.getElementById("previous_list_pending").style.display === "block")
                        document.getElementById("previous_list_pending").style.display = "none";
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
                            var cell5 = row.insertCell(4);
                            var cell6 = row.insertCell(5);
                            var cell7 = row.insertCell(6);
                            var cell8 = row.insertCell(7);
                            var cell9 = row.insertCell(8);
                            var cell10 = row.insertCell(9);
                            cell1.innerHTML = data[i].nif;
                            cell2.innerHTML = data[i].name;
                            cell3.innerHTML = data[i].email;
                            cell4.innerHTML = data[i].address;
                            cell5.innerHTML = data[i].locality;
                            cell6.innerHTML = data[i].phone;
                            cell7.innerHTML = data[i].services;
                            cell8.innerHTML = data[i].creationtime;
                            cell9.innerHTML = data[i].isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style-pend' onclick='activateOrg(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";
                        }

                    }else{
                        alert("Não deu 200.")
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

function activateOrg(row){
    var table = document.getElementById("orgs_pending_table");
    var nif = table.rows[row].cells[0].innerHTML;

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/confirmorg/' + nif,'POST', headers, body)).then(function(response) {

            if (response.status === 200 || response.status === 204) {
                alert("Organização ativa com sucesso.")
            }else{
                alert("Falha ao ativar a organização.")
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

function getPendingReportsNext(){
    standby_rep = [];
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/standbyreports?cursor=' + cursor_next_pending,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("reports_pending_table");

            if (response.status === 200) {
                index_reports++;

                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                document.getElementById("previous_reports_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {
                    reports_reports.push(response.headers.get("Cursor"));
                    cursor_pre_pendingrep = cursor_current_pendingrep;
                    cursor_current_pendingrep = cursor_next_pendingrep;
                    cursor_next_pendingrep = response.headers.get("Cursor");

                    if(document.getElementById("next_reports_pending").style.display === "none")
                        document.getElementById("next_reports_pending").style.display = "block";

                } else{
                    cursor_pre_pendingrep = cursor_current_pendingrep;
                    cursor_current_pendingrep = cursor_next_pendingrep;
                    document.getElementById("next_reports_pending").style.display = "none";
                }
                response.json().then(function(data) {
                    console.log(JSON.stringify(data));
                    if(data != null){
                        var i;
                        for(i = 0; i < data.length; i++){
                            standby_rep.push(data[i].report);
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            var cell4 = row.insertCell(3);
                            var cell5 = row.insertCell(4);
                            var cell6 = row.insertCell(5);
                            var cell7 = row.insertCell(6);
                            var cell8 = row.insertCell(7);
                            cell1.innerHTML = data[i].title;
                            cell2.innerHTML = data[i].address;
                            cell3.innerHTML = data[i].gravity;
                            cell4.innerHTML = data[i].username;
                            cell5.innerHTML = data[i].lat;
                            cell6.innerHTML = data[i].lng;
                            cell7.innerHTML = data[i].creationtime;
                            cell8.outerHTML = "<button type='submit' class='btn-circle btn-primary-style-pend' onclick='activateReport(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";
                        }

                    }else{
                        alert("Não deu 200.")
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

function getPendingReportsPre(){
    standby_rep = [];
    if(index_reports -1 === 0) getPendingReportsFirst();
    else {
        var headers = new Headers();
        var body = "";
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));


        fetch(restRequest('/api/admin/standbyreports?cursor=' + cursor_pre_pending, 'GET', headers, body)).then(function (response) {
                var table = document.getElementById("orgs_pending_table");

                if (response.status === 200) {
                    index_reports--;
                    if (table.rows.length > 1) {
                        table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                    }

                    document.getElementById("previous_reports_pending").style.display = "block";

                    if (response.headers.get("Cursor") !== null) {

                        cursor_next_pendingrep = cursor_current_pendingrep;
                        cursor_current_pendingrep = cursor_pre_pendingrep;
                        cursor_pre_pendingrep = reports_cursors[index_reports - 1];

                        if (document.getElementById("next_reports_pending").style.display === "none")
                            document.getElementById("next_reports_pending").style.display = "block";

                    } else {
                        if (document.getElementById("next_reports_pending").style.display === "block")
                            document.getElementById("next_reports_pending").style.display = "none";
                    }
                    response.json().then(function (data) {
                        console.log(JSON.stringify(data));
                        if (data != null) {
                            var i;
                            for (i = 0; i < data.length; i++) {
                                standby_rep.push(data[i].report);
                                var row = table.insertRow(-1);
                                var cell1 = row.insertCell(0);
                                var cell2 = row.insertCell(1);
                                var cell3 = row.insertCell(2);
                                var cell4 = row.insertCell(3);
                                var cell5 = row.insertCell(4);
                                var cell6 = row.insertCell(5);
                                var cell7 = row.insertCell(6);
                                var cell8 = row.insertCell(7);
                                cell1.innerHTML = data[i].title;
                                cell2.innerHTML = data[i].address;
                                cell3.innerHTML = data[i].gravity;
                                cell4.innerHTML = data[i].username;
                                cell5.innerHTML = data[i].lat;
                                cell6.innerHTML = data[i].lng;
                                cell7.innerHTML = data[i].creationtime;
                                cell8.outerHTML = "<button type='submit' class='btn-circle btn-primary-style-pend' onclick='activateReport(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";
                            }

                        } else {
                            alert("Não deu 200.")
                        }
                    });

                } else {
                    console.log("Tratar do Forbidden");
                }


            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }
}

function getPendingReportsFirst(){
    reports_cursors = [""];
    standby_rep = [];
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/standbyreports?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("reports_pending_table");

            if (response.status === 200) {
                index_reports = 0;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                document.getElementById("previous_reports_pending").style.display = "none";

                if(response.headers.get("Cursor") !== null) {
                    reports_cursors.push(response.headers.get("Cursor"));
                    cursor_pre_pendingrep = "";
                    cursor_current_pendingrep = "";
                    cursor_next_pendingrep = response.headers.get("Cursor");

                    if(document.getElementById("next_reports_pending").style.display === "none")
                        document.getElementById("next_reports_pending").style.display = "block";
                    if(document.getElementById("previous_reports_pending").style.display === "block")
                        document.getElementById("previous_reports_pending").style.display = "none";
                } else{
                    if(document.getElementById("next_reports_pending").style.display === "block")
                        document.getElementById("next_reports_pending").style.display = "none";
                    if(document.getElementById("previous_reports_pending").style.display === "block")
                        document.getElementById("previous_reports_pending").style.display = "none";
                }
                response.json().then(function(data) {
                    console.log(JSON.stringify(data));
                    if(data != null){
                        var i;
                        for(i = 0; i < data.length; i++){
                            standby_rep.push(data[i].report);
                            var row = table.insertRow(-1);
                            var cell1 = row.insertCell(0);
                            var cell2 = row.insertCell(1);
                            var cell3 = row.insertCell(2);
                            var cell4 = row.insertCell(3);
                            var cell5 = row.insertCell(4);
                            var cell6 = row.insertCell(5);
                            var cell7 = row.insertCell(6);
                            var cell8 = row.insertCell(7);
                            cell1.innerHTML = data[i].title;
                            cell2.innerHTML = data[i].address;
                            cell3.innerHTML = data[i].gravity;
                            cell4.innerHTML = data[i].username;
                            cell5.innerHTML = data[i].lat;
                            cell6.innerHTML = data[i].lng;
                            cell7.innerHTML = data[i].creationtime;
                            cell8.outerHTML = "<button type='submit' class='btn-circle btn-primary-style-pend' onclick='activateReport(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";
                        }

                    }else{
                        alert("Não deu 200.")
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

function activateReport(row){
    var reportId = standby_rep[row];

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/confirmreport/' + reportId,'POST', headers, body)).then(function(response) {

            if (response.status === 200 || response.status === 204) {
                alert("Report ativado com sucesso.")
            }else{
                alert("Falha ao ativar reporte.")
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });

}

function drawPieChart(){

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest("/api/admin/stats/org/applications",'GET', headers, body)).then(function(response) {

            if (response.status === 200 || response.status === 204) {
                response.json().then(function(data){
                    console.log(data);
                    var dados = google.visualization.arrayToDataTable(data);

                    var options = {
                        title: 'Eficácia por organização',
                        width:500,
                        height:500
                    };

                    var chart = new google.visualization.PieChart(document.getElementById('piechart'));

                    chart.draw(dados, options);
                });

            }else{
                alert("Falha ao apagar utilizador.")
            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

function getPublicNext(){
    public_reports = [];
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/publicreports?cursor=' + cursor_next_public,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("public_reports_pending_table");

            if (response.status === 200) {
                index_public++;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                document.getElementById("previous_public_reports_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {
                    public_reports.push(response.headers.get("Cursor"));
                    cursor_pre_public = cursor_current_public;
                    cursor_current_public = cursor_next_public;
                    cursor_next_public = response.headers.get("Cursor");

                    if(document.getElementById("next_public_reports_pending").style.display === "none")
                        document.getElementById("next_public_reports_pending").style.display = "block";

                } else{
                    cursor_pre_public = cursor_current_public;
                    cursor_current_public = cursor_next_public;
                    document.getElementById("next_public_reports_pending").style.display = "none";
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
                            var cell5 = row.insertCell(4);
                            var cell6 = row.insertCell(5);
                            var cell7 = row.insertCell(6);
                            var cell8 = row.insertCell(7);
                            var cell9 = row.insertCell(8);
                            cell1.innerHTML = data[i].title;
                            cell2.innerHTML = data[i].address;
                            cell3.innerHTML = data[i].gravity;
                            cell4.innerHTML = data[i].username;
                            cell5.innerHTML = data[i].lat;
                            cell6.innerHTML = data[i].lng;
                            var orgs = data[i].applications;
                            if(orgs !== undefined) {
                                var options = "<option value='' disabled selected>Selecione a Organização</option>";
                                for (var j = 0; j < orgs.length; j++) {
                                    options += "<option>" + orgs[j].name + "</option>"
                                }

                                cell7.innerHTML = "<select className='dropdown-m' id='drop'" + i + ">" + options +

                                    "</select>";

                                public_reports.push({report: data[i].report, applications: data[i].applications});

                                cell9.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activatePublicReport(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";


                            } else if(data[i].org !== undefined){
                                cell7.innerHTML = "<p>" + data[i].org.nif +"-"+ data[i].org.name + "</p>";
                                public_reports.push({});
                            } else{
                                cell7.innerHTML = "-";
                                public_reports.push({});
                            }
                            cell8.innerHTML = data[i].creationtime;
                        }

                    }else{
                        alert("Não deu 200.")
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

function getPublicPre(){
    public_reports = [];
    if( index_public- 1 === 0) getPublicFirst();
    else {
        var headers = new Headers();
        var body = "";
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));


        fetch(restRequest('/api/admin/publicreports?cursor=' + cursor_pre_public, 'GET', headers, body)).then(function (response) {
                var table = document.getElementById("public_reports_pending_table");

                if (response.status === 200) {
                    index_public--;
                    if (table.rows.length > 1) {

                        table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                    }

                    document.getElementById("previous_public_reports_pending").style.display = "block";

                    if (response.headers.get("Cursor") !== null) {

                        cursor_next_public = cursor_current_public;
                        cursor_current_public = cursor_pre_public;
                        cursor_pre_public = public_cursors[index_public - 1];

                        if (document.getElementById("next_public_reports_pending").style.display === "none")
                            document.getElementById("next_public_reports_pending").style.display = "block";

                    } else {

                        if (document.getElementById("next_public_reports_pending").style.display === "block")
                            document.getElementById("next_public_reports_pending").style.display = "none";
                    }
                    response.json().then(function (data) {
                        console.log(JSON.stringify(data));
                        if (data != null) {
                            var i;
                            for (i = 0; i < data.length; i++) {
                                var row = table.insertRow(-1);
                                var cell1 = row.insertCell(0);
                                var cell2 = row.insertCell(1);
                                var cell3 = row.insertCell(2);
                                var cell4 = row.insertCell(3);
                                var cell5 = row.insertCell(4);
                                var cell6 = row.insertCell(5);
                                var cell7 = row.insertCell(6);
                                var cell8 = row.insertCell(7);
                                var cell9 = row.insertCell(8);
                                cell1.innerHTML = data[i].title;
                                cell2.innerHTML = data[i].address;
                                cell3.innerHTML = data[i].gravity;
                                cell4.innerHTML = data[i].username;
                                cell5.innerHTML = data[i].lat;
                                cell6.innerHTML = data[i].lng;
                                var orgs = data[i].applications;
                                if (orgs !== undefined) {
                                    var options = "<option value='' disabled selected>Select your option</option>";
                                    for (var j = 0; j < orgs.length; j++) {
                                        options += "<option>" + orgs[j].name + "</option>"
                                    }

                                    cell7.innerHTML = "<select className='dropdown-m' id='drop'" + i + ">" + options +

                                        "</select>";

                                    public_reports.push({report: data[i].report, applications: data[i].applications});

                                    cell9.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activatePublicReport(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";


                                } else if (data[i].org !== undefined) {
                                    cell7.innerHTML = "<p>" + data[i].org.nif + "-" + data[i].org.name + "</p>";
                                    public_reports.push({});
                                } else {
                                    cell7.innerHTML = "-";
                                    public_reports.push({});
                                }
                                cell8.innerHTML = data[i].creationtime;
                            }

                        } else {
                            alert("Não deu 200.")
                        }
                    });

                } else {
                    console.log("Tratar do Forbidden");
                }


            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }
}

function getPublicFirst(){
    public_cursors = [""];
    public_reports = [];
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/publicreports?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("public_reports_pending_table");

            if (response.status === 200) {
                index_public = 0;
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                document.getElementById("previous_public_reports_pending").style.display = "none";
                if(response.headers.get("Cursor") !== null) {
                    public_cursors.push(response.headers.get("Cursor"));
                    cursor_pre_public = "";
                    cursor_current_public = "";
                    cursor_next_public = response.headers.get("Cursor");

                    if(document.getElementById("next_public_reports_pending").style.display === "none")
                        document.getElementById("next_public_reports_pending").style.display = "block";
                    if(document.getElementById("previous_public_reports_pending").style.display === "block")
                        document.getElementById("previous_public_reports_pending").style.display = "none";
                } else{
                    if(document.getElementById("next_public_reports_pending").style.display === "block")
                        document.getElementById("next_public_reports_pending").style.display = "none";
                    if(document.getElementById("previous_public_reports_pending").style.display === "block")
                        document.getElementById("previous_public_reports_pending").style.display = "none";
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
                            var cell5 = row.insertCell(4);
                            var cell6 = row.insertCell(5);
                            var cell7 = row.insertCell(6);
                            var cell8 = row.insertCell(7);
                            var cell9 = row.insertCell(8);
                            cell1.innerHTML = data[i].title;
                            cell2.innerHTML = data[i].address;
                            cell3.innerHTML = data[i].gravity;
                            cell4.innerHTML = data[i].username;
                            cell5.innerHTML = data[i].lat;
                            cell6.innerHTML = data[i].lng;
                            var orgs = data[i].applications;
                            if(orgs !== undefined) {
                                var options = "<option value='' disabled selected>Select your option</option>";
                                for (var j = 0; j < orgs.length; j++) {
                                    options += "<option>" + orgs[j].name + "</option>"
                                }

                                cell7.innerHTML = "<select className='dropdown-m' id='drop'" + i + ">" + options +

                                    "</select>";

                                public_reports.push({report: data[i].report, applications: data[i].applications});

                                cell9.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activatePublicReport(this.parentNode.rowIndex)'><a class='fa fa-check'></button>";


                            } else if(data[i].org !== undefined){
                                cell7.innerHTML = "<p>" + data[i].org.nif +"-"+ data[i].org.name + "</p>";
                                public_reports.push({});
                            } else{
                                cell7.innerHTML = "-";
                                public_reports.push({});
                            }
                            cell8.innerHTML = data[i].creationtime;
                        }

                    }else{
                        alert("Não deu 200.")
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

function activatePublicReport(row){

    var table = document.getElementById("public_reports_pending_table");



    var report = public_reports[row - 1];
    var reportId = report.report;

    var org = report.applications[table.rows[row].cells[6].firstChild.selectedIndex - 1];

    var yes = prompt("Budget: " + org.budget + "Info: " + org.info, "Pressione 's' se sim ou 'n' se não pretende aceitar este candidato:" );

    if(yes === "s") {

        var headers = new Headers();
        var body = JSON.stringify({
            report: reportId,
            nif: org.nif
        });
        headers.append('Authorization', localStorage.getItem('token'));
        headers.append('Device-Id', localStorage.getItem('fingerprint'));
        headers.append('Device-App', localStorage.getItem('app'));
        headers.append('Device-Info', localStorage.getItem('browser'));


        fetch(restRequest('/api/report/acceptapplication', 'POST', headers, body)).then(function (response) {

                if (response.status === 200 || response.status === 204) {
                    alert("Organização atribuida com sucesso.")
                } else {
                    alert("Falha ao atribuir organização.")
                }

            }
        )
            .catch(function (err) {
                console.log('Fetch Error', err);
            });
    }
}

function translate(category){
    var cat= "";
    switch (category) {
        case "LIMPEZA":
            cat = "Limpeza de terrenos";
            break;
        case "COMBUSTIVEL":
            cat = "Transportes combustível";
            break;
        case "ELETRICIDADE":
            cat = "Material elétrico";
            break;

    }
    return cat;
}

function getTopUsers(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));

    fetch(restRequest('/api/profile/usertop','GET', headers, body)).then(function (response){
        if (response.status === 200) {
            response.json().then(function(data) {
                var table = document.getElementById("top_table");
                if(data != null){
                    var i;
                    for(i = 0; i < data.length; i++){
                        var row = table.insertRow(-1);
                        var cell1 = row.insertCell(0);
                        var cell2 = row.insertCell(1);
                        var cell3 = row.insertCell(2);
                        cell1.innerHTML = data[i].place;
                        cell2.innerHTML = data[i].username;
                        cell3.innerHTML = data[i].points;
                    }
                }
            });

        } else {
            alert("Falha ao pedir top utilizadores.")
        }
    })
        .catch(function (err) {
            console.log('Fetch Error', err);
        });
}

function drawGeoChart(){

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest("/api/admin/stats/reports/map",'GET', headers, body)).then(response => response.text())
  .then(svg => document.getElementById("geomap").insertAdjacentHTML("afterbegin", svg));
}

function monthStat(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest("/api/admin/stats/reports/months",'GET', headers, body)).then(function(response) {

            if (response.status === 200) {
                response.json().then(function(data){
                    console.log(data);
                    var dados = google.visualization.arrayToDataTable(data);

                    var options = {
                        title: 'Número de ocorrências por mês do ano',
                        hAxis: {
                          title: 'Mês do ano'
                        },
                        vAxis: {
                          title: 'Número de Ocorrências'
                        }
                      };

                    var chart = new google.visualization.ColumnChart(document.getElementById('monthsGraph'));

                    chart.draw(dados, options);
                });

            }

        }
    )
        .catch(function(err) {
            console.log('Fetch Error', err);
        });
}

function getProfile(row){
    console.log(row);
}

