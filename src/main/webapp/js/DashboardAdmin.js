function init() {

    verifyIsLoggedIn();
    document.getElementById("logout_button").onclick = logOut;

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