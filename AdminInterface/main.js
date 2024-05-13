// Importing necessary functions from Firebase SDK
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.11.1/firebase-app.js";
import { getFirestore, collection, query, where, getDocs, doc, updateDoc } from "https://www.gstatic.com/firebasejs/10.11.1/firebase-firestore.js";

const firebaseConfig = {
    apiKey: "AIzaSyDK_O42K3Fgz-GAZwXQFCQGWUCdk9XZz2c",
    authDomain: "dmucabapp.firebaseapp.com",
    projectId: "dmucabapp",
    storageBucket: "dmucabapp.appspot.com",
    messagingSenderId: "404879806612",
    appId: "1:404879806612:web:cdd7a41737c00a9e002481"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

async function fetchDriverData() {
    const driverContainer = document.getElementById('driverContainer');
    const driversQuery = query(collection(db, "users"), where("driverAccountIsActive", "==", false));

    const querySnapshot = await getDocs(driversQuery);
    querySnapshot.forEach(doc => {
        const data = doc.data();
        const driverCard = document.createElement('div');
        driverCard.className = 'driver-card';
        driverCard.innerHTML = `
            <div class="driver-info"><strong>Name:</strong> ${data.name}</div>
            <div class="driver-info"><strong>Email:</strong> ${data.email}</div>
            <div class="driver-info"><strong>Phone:</strong> ${data.phone}</div>
            <div class="driver-images">
                <img src="${data.idDocument || 'default-id.jpg'}" alt="ID Document">
                <img src="${data.insuranceCertificateImageUrl || 'default-insurance.jpg'}" alt="Insurance Certificate">
                <img src="${data.profileImageUrl || 'default-profile.jpg'}" alt="Profile Image">
            </div>
            <button id="activate-${doc.id}">Activate Account</button>
        `;
        driverContainer.appendChild(driverCard);
        document.getElementById(`activate-${doc.id}`).addEventListener('click', () => activateDriverAccount(doc.id));
    });
}

async function activateDriverAccount(docId) {
    const driverRef = doc(db, "users", docId);
    try {
        await updateDoc(driverRef, {
            driverAccountIsActive: true
        });
        console.log(`Account activated for driver with ID: ${docId}`);
        alert('Driver account activated successfully.');
        document.querySelector(`#activate-${docId}`).disabled = true; 
    } catch (error) {
        console.error("Error updating document: ", error);
        alert('Failed to activate driver account.');
    }
}

// Call the function to execute it
fetchDriverData();
