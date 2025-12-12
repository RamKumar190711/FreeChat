const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

exports.sendNotification = functions.https.onCall(async (data, context) => {
    const { toUsername, title, body } = data;

    if (!toUsername) {
        return { error: 'Missing receiver username' };
    }

    // Get receiver's FCM token from Firestore
    const tokenDoc = await admin.firestore()
        .collection('fcmTokens')
        .doc(toUsername)
        .get();

    if (!tokenDoc.exists) {
        return { error: 'Receiver has no FCM token' };
    }

    const token = tokenDoc.data().token;

    const message = {
        token: token,
        notification: {
            title: title,
            body: body
        }
    };

    await admin.messaging().send(message);

    return { success: true };
});
