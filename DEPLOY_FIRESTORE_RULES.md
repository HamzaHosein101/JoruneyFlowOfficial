# Deploy Firestore Rules - Quick Guide

## The Problem
You're seeing "Permission denied" when trying to make users admin because the updated Firestore rules haven't been deployed to Firebase yet.

## Solution: Deploy Rules via Firebase Console

### Step 1: Open Firebase Console
1. Go to https://console.firebase.google.com/
2. Select your project (JourneyFlow or your project name)

### Step 2: Navigate to Firestore Rules
1. In the left sidebar, click **"Firestore Database"**
2. Click on the **"Rules"** tab at the top

### Step 3: Copy and Paste Rules
1. Open the `firestore.rules` file in this project
2. **Copy the ENTIRE contents** of the file (Ctrl+A, Ctrl+C)
3. **Paste it** into the Firebase Console rules editor (Ctrl+V)
4. The editor should show the rules with syntax highlighting

### Step 4: Publish Rules
1. Click the **"Publish"** button (usually at the top right)
2. Wait for the confirmation message: "Rules published successfully"
3. Rules typically take 10-30 seconds to propagate

### Step 5: Test
1. Go back to your app
2. Try making a user admin again
3. The "Make Admin" button should now work!

## Verify Your Admin Account
Before testing, make sure:
- Your current user account has `role: "admin"` set in Firestore
- You can check this in Firebase Console → Firestore Database → users collection → your user document

## If Still Not Working

### Check 1: Verify Rules Are Deployed
- In Firebase Console → Firestore → Rules tab
- Look for a timestamp showing when rules were last published
- If you see old rules, they weren't updated

### Check 2: Verify Your Admin Role
- In Firebase Console → Firestore Database
- Go to `users` collection
- Find your user document (by your UID)
- Check that it has a field `role` with value `"admin"`

### Check 3: Wait a Bit Longer
- Rules can take up to 1-2 minutes to fully propagate
- Try again after waiting

### Check 4: Clear App Cache
- Close and reopen the app
- Or sign out and sign back in

## Alternative: Deploy via Firebase CLI

If you have Firebase CLI installed:

```bash
# Install Firebase CLI (if not installed)
npm install -g firebase-tools

# Login to Firebase
firebase login

# Deploy rules
firebase deploy --only firestore:rules
```

## What the Rules Do

The key rule that allows admins to update user roles is on line 34:

```
allow update: if isPathOwner(userId) || (isAdmin() && userId != uid());
```

This means:
- ✅ Users can update their own documents
- ✅ Admins can update OTHER users' documents (including setting/removing admin role)
- ❌ Admins cannot update their own document (to prevent accidental self-demotion)

The `isAdmin()` function checks if the current user has `role == "admin"` in their user document.

