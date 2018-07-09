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

var current_position = "list_users_variable";

var URL_BASE = 'https://main-dot-mimetic-encoder-209111.appspot.com';

init();

function init() {


    verifyIsLoggedIn();

    google.charts.load('current', {'packages':['corechart']});
    google.charts.setOnLoadCallback(drawChart);

    getFirstUsers();
    getPendingFirst();
    getPendingReportsFirst();
    getPublicFirst();

    document.getElementById("logout_button").onclick = logOut;
    document.getElementById("public_reports_button").onclick = showPublicReports;
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
    document.getElementById("next_report_pending").onclick = getPendingReportsNext;
    document.getElementById("previous_reports_pending").onclick = getPendingReportsPre;
    document.getElementById("refresh_reports_pending").onclick = getPendingReportsFirst;
    document.getElementById("next_public_report_pending").onclick = getPublicNext;
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

function hideShow(element){

    if(current_position === "list_users_variable"){

        document.getElementById("list_users").style.display = "none";

    } else if(current_position === "show_pending_variable"){

        document.getElementById("list_pending_orgs").style.display = "none";

    } else if(current_position === "list_public_variable"){

        document.getElementById("list_pending_public_reports").style.display = "none";

    } else if(current_position === "statistics_variable"){

        document.getElementById("piechart").style.display = "none";

    } else if(current_position === "reports_pending_variable"){

        document.getElementById("list_pending_reports").style.display = "none";

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

        document.getElementById("piechart").style.display = "block";
        current_position = "statistics_variable";

    } else if(element === "reports_pending_variable"){

        document.getElementById("list_pending_reports").style.display = "block";
        current_position = "reports_pending_variable";

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

function promoDepromo (row){
    var table = document.getElementById("user_table");
    var username = table.rows[row].cells[0].innerHTML;
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));
    if(table.rows[row].cells[2].innerHTML === "ADMIN"){
        fetch(restRequest('/api/admin/demote/' + username, 'POST', headers, body)).then(function(response) {

                if (response.status === 200 || response.status === 204) {
                    alert("Trabalhador despromovido com sucesso.")
                }else{
                    alert("Falha ao despromover o utilizador.")
                }

            }
        )
            .catch(function(err) {
                console.log('Fetch Error', err);
            });
    } else{
        fetch(restRequest('/api/admin/promote/' + username, 'POST', headers, body)).then(function(response) {

                if (response.status === 200 || response.status === 204) {
                    alert("Trabalhador promovido com sucesso.")
                }else{
                    alert("Falha ao promover o utilizador.")
                }

            }
        )
            .catch(function(err) {
                console.log('Fetch Error', err);
            });
    }
}

function getFirstUsers(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/userlist?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(response.headers.get("Cursor") !== null) {
                    cursor_pre = "";
                    cursor_current = "";
                    cursor_next = response.headers.get("Cursor");
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
                            var cell5 = row.insertCell(4);
                            cell1.innerHTML = data[i].username;
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].level;
                            cell4.innerHTML = data[i].points;
                            cell5.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='promoDepromo(this.parentNode.rowIndex)'></button>";
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
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/userlist?cursor=' + cursor_next,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(document.getElementById("previous_list").style.display === "none")
                    document.getElementById("previous_list").style.display = "block";
                if(response.headers.get("Cursor") !== null) {

                    cursor_pre = cursor_current;
                    cursor_current = cursor_next;
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
                            cell1.innerHTML = data[i].username;
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].level;
                            cell4.innerHTML = data[i].points;
                            cell5.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='promoDepromo(this.parentNode.rowIndex)'></button>";
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
    if(cursor_pre === "") getFirstUsers();

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/userlist?cursor=' + cursor_pre,'GET', headers, body)).then(function (response) {
            var table = document.getElementById("user_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if (document.getElementById("previous_list").style.display === "none")
                    document.getElementById("previous_list").style.display = "block";
                if (response.headers.get("Cursor") !== null) {

                    cursor_next= cursor_current;
                    cursor_current = cursor_pre;
                    cursor_pre = response.headers.get("Cursor");

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
                            cell1.innerHTML = data[i].username;
                            cell2.innerHTML = data[i].email;
                            cell3.innerHTML = data[i].level;
                            cell4.innerHTML = data[i].points;
                            cell5.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='promoDepromo(this.parentNode.rowIndex)'></button>";
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
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if(document.getElementById("previous_list_pending").style.display === "none")
                    document.getElementById("previous_list_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {

                    cursor_pre_pending = cursor_current_pending;
                    cursor_current_pending = cursor_next_pending;
                    cursor_next_pending = response.headers.get("Cursor");

                    if(document.getElementById("next_list_pending").style.display === "none")
                        document.getElementById("next_list_pending").style.display = "block";

                } else{
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
                            cell7.innerHTML = data[i].services;
                            cell8.innerHTML = data[i].creationtime;
                            cell9.innerHTML = data[i].isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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
    if(cursor_pre_pending === "") getPendingFirst();

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/orgstoconfirm?cursor=' + cursor_pre_pending,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if (document.getElementById("previous_list_pending").style.display === "none")
                    document.getElementById("previous_list_pending").style.display = "block";

                if (response.headers.get("Cursor") !== null) {

                    cursor_next_pending= cursor_current_pending;
                    cursor_current_pending = cursor_pre_pending;
                    cursor_pre_pending = response.headers.get("Cursor");

                    if (document.getElementById("next_list_pending").style.display === "none")
                        document.getElementById("next_list_pending").style.display = "block";

                } else {
                    if (document.getElementById("next_list_pending").style.display === "block")
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
                            cell7.innerHTML = data[i].services;
                            cell8.innerHTML = data[i].creationtime;
                            cell9.innerHTML = data[i].isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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

function getPendingFirst(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/orgstoconfirm?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(response.headers.get("Cursor") !== null) {

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
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style-pend' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/standbyreports?cursor=' + cursor_next_pending,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("reports_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if(document.getElementById("previous_reports_pending").style.display === "none")
                    document.getElementById("previous_reports_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {

                    cursor_pre_pendingrep = cursor_current_pendingrep;
                    cursor_current_pendingrep = cursor_next_pendingrep;
                    cursor_next_pendingrep = response.headers.get("Cursor");

                    if(document.getElementById("next_reports_pending").style.display === "none")
                        document.getElementById("next_reports_pending").style.display = "block";

                } else{
                    if(document.getElementById("next_reports_pending").style.display === "block")
                        document.getElementById("next_reports_pending").style.display = "none";
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
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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
    if(cursor_pre_pendingrep === "") getPendingReportsFirst();

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/standbyreports?cursor=' + cursor_pre_pending,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if (document.getElementById("previous_reports_pending").style.display === "none")
                    document.getElementById("previous_reports_pending").style.display = "block";

                if (response.headers.get("Cursor") !== null) {

                    cursor_next_pendingrep = cursor_current_pendingrep;
                    cursor_current_pendingrep = cursor_pre_pendingrep;
                    cursor_pre_pendingrep = response.headers.get("Cursor");

                    if (document.getElementById("next_reports_pending").style.display === "none")
                        document.getElementById("next_reports_pending").style.display = "block";

                } else {
                    if (document.getElementById("next_reports_pending").style.display === "block")
                        document.getElementById("next_reports_pending").style.display = "none";
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
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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

function getPendingReportsFirst(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/standbyreports?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("reports_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(response.headers.get("Cursor") !== null) {

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
                            cell1.innerHTML = data[i].report;
                            cell2.innerHTML = data[i].title;
                            cell3.innerHTML = data[i].address;
                            cell4.innerHTML = data[i].gravity;
                            cell5.innerHTML = data[i].username;
                            cell6.innerHTML = data[i].lat;
                            cell7.innerHTML = data[i].lng;
                            cell8.innerHTML = data[i].creationtime;
                            cell9.outerHTML = "<button type='submit' class='btn-circle btn-primary-style-pend' onclick='activateReport(this.parentNode.rowIndex)'></button>";
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
    var table = document.getElementById("reports_pending_table");
    var report = table.rows[row].cells[0].innerHTML;

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/confirmreport/' + report,'POST', headers, body)).then(function(response) {

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

function drawChart(){

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
                        title: 'Eficácia por organização'

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
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/publicreports?cursor=' + cursor_next_public,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("public_reports_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if(document.getElementById("previous_public_reports_pending").style.display === "none")
                    document.getElementById("previous_public_reports_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {

                    cursor_pre_public = cursor_current_public;
                    cursor_current_public = cursor_next_public;
                    cursor_next_public = response.headers.get("Cursor");

                    if(document.getElementById("next_public_reports_pending").style.display === "none")
                        document.getElementById("next_public_reports_pending").style.display = "block";

                } else{
                    if(document.getElementById("next_public reports_pending").style.display === "block")
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
                            var cell10 = row.insertCell(9);
                            cell1.innerHTML = data[i].report;
                            cell2.innerHTML = data[i].title;
                            cell3.innerHTML = data[i].address;
                            cell4.innerHTML = data[i].gravity;
                            cell5.innerHTML = data[i].username;
                            cell6.innerHTML = data[i].lat;
                            cell7.innerHTML = data[i].lng;
                            var orgs = data[i].orgs;
                            var options = "<option value='' disabled selected>Select your option</option>";
                            for(var j = 0; j< orgs.length; j++){
                                options += "<option value = " + orgs[j].nif + ">" + orgs[j].name + "</option>"
                            }
                            cell8.innerHTML = "<select className='dropdown-m' id='drop1'>" + options +

                                "</select>";
                            cell9.innerHTML = data[i].creationtime;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activatePublicReport(this.parentNode.rowIndex)'></button>";
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
    if(cursor_pre_public === "") getPublicFirst();

    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/publicreports?cursor=' + cursor_pre_public,'GET', headers, body)).then(function(response) {
            var table = document.getElementById("public_reports_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if (document.getElementById("previous_public_reports_pending").style.display === "none")
                    document.getElementById("previous_public_reports_pending").style.display = "block";

                if (response.headers.get("Cursor") !== null) {

                    cursor_next_public= cursor_current_public;
                    cursor_current_public = cursor_pre_public;
                    cursor_pre_public = response.headers.get("Cursor");

                    if (document.getElementById("next_public_reports_pending").style.display === "none")
                        document.getElementById("next_public_reports_pending").style.display = "block";

                } else {
                    if (document.getElementById("next_public_reports_pending").style.display === "block")
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
                            var cell10 = row.insertCell(9);
                            cell1.innerHTML = data[i].report;
                            cell2.innerHTML = data[i].title;
                            cell3.innerHTML = data[i].address;
                            cell4.innerHTML = data[i].gravity;
                            cell5.innerHTML = data[i].username;
                            cell6.innerHTML = data[i].lat;
                            cell7.innerHTML = data[i].lng;
                            var orgs = data[i].orgs;
                            var options = "<option value='' disabled selected>Select your option</option>";
                            for(var j = 0; j< orgs.length; j++){
                                options += "<option value = " + orgs[j].nif + ">" + orgs[j].name + "</option>"
                            }
                            cell8.innerHTML = "<select className='dropdown-m' id='drop1'>" + options +

                                "</select>";
                            cell9.innerHTML = data[i].creationtime;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activatePublicReport(this.parentNode.rowIndex)'></button>";
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

function getPublicFirst(){
    var headers = new Headers();
    var body = "";
    headers.append('Authorization', localStorage.getItem('token'));
    headers.append('Device-Id', localStorage.getItem('fingerprint'));
    headers.append('Device-App', localStorage.getItem('app'));
    headers.append('Device-Info', localStorage.getItem('browser'));


    fetch(restRequest('/api/admin/publicreports?cursor=','GET', headers, body)).then(function(response) {
            var table = document.getElementById("public_reports_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }
                if(response.headers.get("Cursor") !== null) {

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
                            var cell10 = row.insertCell(9);
                            cell1.innerHTML = data[i].report;
                            cell2.innerHTML = data[i].title;
                            cell3.innerHTML = data[i].address;
                            cell4.innerHTML = data[i].gravity;
                            cell5.innerHTML = data[i].username;
                            cell6.innerHTML = data[i].lat;
                            cell7.innerHTML = data[i].lng;
                            var orgs = data[i].applications;
                            var options = "<option value='' disabled selected>Select your option</option>";
                            for(var j = 0; j< orgs.length; j++){
                                options += "<option value = " + orgs[j].nif + "," + orgs[j].budget + ">" + orgs[j].name + "</option>"
                            }
                            cell8.innerHTML = "<select className='dropdown-m' id='drop1'>" + options +

                                "</select>";
                            cell9.innerHTML = data[i].creationtime;
                            cell10.outerHTML = "<button type='submit' class='btn-circle btn-primary-style' onclick='activatePublicReport(this.parentNode.rowIndex)'></button>";
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
    var reportId = table.rows[row].cells[0].innerHTML;
    var index = table.rows[row].cells[7].value.indexOf(",");
    var nif = table.rows[row].cells[7].value.substring(0, index);
    var budget = table.rows[row].cells[7].value.substring(index);
    console.log(index);
    console.log(nif);
    console.log(budget);

    var yes = prompt("Budget: " + budget, "Pressione 's' se sim ou 'n' se não pretende aceitar este candidato:" );

    if(yes === "s") {

        var headers = new Headers();
        var body = JSON.stringify({
            report: reportId,
            nif: nif
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