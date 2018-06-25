var cursor_next;
var cursor_pre;
var cursor_current;
var cursor_next_pending;
var cursor_current_pending;
var cursor_pre_pending;

var URL_BASE = 'https://hardy-scarab-200218.appspot.com';

init();

function init() {

    verifyIsLoggedIn();

    getFirstUsers();
    getPendingFirst();

    document.getElementById("logout_button").onclick = logOut;
    document.getElementById("next_list").onclick = getNextUsers;
    document.getElementById("previous_list").onclick = getPreUsers;
    document.getElementById("refresh_users").onclick = getFirstUsers;
    document.getElementById("next_list_pending").onclick = getPendingNext;
    document.getElementById("previous_list_pending").onclick = getPendingPre;
    document.getElementById("refresh_orgs_pending").onclick = getPendingFirst;

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
                window.location.href = "../index.html";
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
    if(table.rows[row].cells[2].innerHTML === "ADMIN"){

        fetch(URL_BASE + '/api/admin/demote/' + username, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': localStorage.getItem('token')
            }
        }).then(function(response) {

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
        fetch(URL_BASE + '/api/admin/promote/' + username, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': localStorage.getItem('token')
            }
        }).then(function(response) {

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
    fetch(URL_BASE + '/api/admin/userlist?cursor=', {
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
                            cell1.innerHTML = data[i].User;
                            cell2.innerHTML = data[i].user_email;
                            cell3.innerHTML = data[i].user_level;
                            cell4.innerHTML = data[i].userpoints_points;
                            cell5.outerHTML = "<button type='submit' class='btn btn-primary-style' onclick='promoDepromo(this.parentNode.rowIndex)'></button>";
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
    fetch(URL_BASE + '/api/admin/userlist?cursor=' + cursor_next, {
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
                            cell1.innerHTML = data[i].User;
                            cell2.innerHTML = data[i].user_email;
                            cell3.innerHTML = data[i].user_level;
                            cell4.innerHTML = data[i].userpoints_points;
                            cell5.outerHTML = "<button type='submit' class='btn btn-primary-style' onclick='promoDepromo(this.parentNode.rowIndex)'></button>";
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

    else {
        fetch(URL_BASE + '/api/admin/listworkers?cursor=' + cursor_pre, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': localStorage.getItem('token')
            }
        }).then(function (response) {
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
                                cell1.innerHTML = data[i].User;
                                cell2.innerHTML = data[i].user_email;
                                cell3.innerHTML = data[i].user_level;
                                cell4.innerHTML = data[i].userpoints_points;
                                cell5.outerHTML = "<button type='submit' class='btn btn-primary-style' onclick='promoDepromo(this.parentNode.rowIndex)'></button>";
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

function getPendingNext(){
    fetch(URL_BASE + '/api/admin/orgstoconfirm?cursor=' + cursor_next_pending, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if(document.getElementById("previous_list_pending").style.display === "none")
                    document.getElementById("previous_list_pending").style.display = "block";

                if(response.headers.get("Cursor") !== null) {

                    cursor_pre_pending = cursor_current;
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
                            cell1.innerHTML = data[i].Org;
                            cell2.innerHTML = data[i].org_name;
                            cell4.innerHTML = data[i].org_email;
                            cell3.innerHTML = data[i].org_address;
                            cell5.innerHTML = data[i].org_locality;
                            cell6.innerHTML = data[i].org_phone;
                            cell7.innerHTML = data[i].org_services;
                            cell8.innerHTML = data[i].org_creationtime;
                            cell9.innerHTML = data[i].org_isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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
    if(cursor_pre_workers === "") getPendingFirst();

    fetch(URL_BASE + '/api/admin/orgstoconfirm?cursor=' + cursor_pre_pending, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {
            var table = document.getElementById("orgs_pending_table");

            if (response.status === 200) {
                if(table.rows.length > 1) {
                    table.getElementsByTagName("tbody")[0].innerHTML = table.rows[0].innerHTML;
                }

                if (document.getElementById("previous_list_pending").style.display === "none")
                    document.getElementById("previous_list_pending").style.display = "block";

                if (response.headers.get("Cursor") !== null) {

                    cursor_next_pending= cursor_current_pending;
                    cursor_current_pending = cursor_pre;
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
                            cell1.innerHTML = data[i].Org;
                            cell2.innerHTML = data[i].org_name;
                            cell4.innerHTML = data[i].org_email;
                            cell3.innerHTML = data[i].org_address;
                            cell5.innerHTML = data[i].org_locality;
                            cell6.innerHTML = data[i].org_phone;
                            cell7.innerHTML = data[i].org_services;
                            cell8.innerHTML = data[i].org_creationtime;
                            cell9.innerHTML = data[i].org_isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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
    fetch(URL_BASE + '/api/admin/orgstoconfirm?cursor=', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {
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
                            cell1.innerHTML = data[i].Org;
                            cell2.innerHTML = data[i].org_name;
                            cell4.innerHTML = data[i].org_email;
                            cell3.innerHTML = data[i].org_address;
                            cell5.innerHTML = data[i].org_locality;
                            cell6.innerHTML = data[i].org_phone;
                            cell7.innerHTML = data[i].org_services;
                            cell8.innerHTML = data[i].org_creationtime;
                            cell9.innerHTML = data[i].org_isfirestation;
                            cell10.outerHTML = "<button type='submit' class='btn btn-primary-style' onclick='activateOrg(this.parentNode.rowIndex)'></button>";
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
    fetch(URL_BASE + '/api/admin/confirmorg/' + nif, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('token')
        }
    }).then(function(response) {

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

