# Firebase Firestore Rules Setup Instructions

## Problem
You're getting "PERMISSION_DENIED" when trying to report reviews because Firestore security rules need to allow authenticated users to update the `reportCount` field.

## Solution

### Step 1: Go to Firebase Console
1. Open https://console.firebase.google.com/
2. Select your project

### Step 2: Navigate to Firestore Rules
1. Click "Firestore Database" in the left sidebar
2. Click on the "Rules" tab at the top

### Step 3: Copy and Paste the Rules
Copy the ENTIRE contents of the `firestore.rules` file and paste it into the Firebase Console rules editor.

### Step 4: Publish
1. Click the "Publish" button
2. Wait for confirmation that rules have been deployed

### Step 5: Test
Try reporting a review again. It should work now!

## Alternative: Deploy via CLI (if you have Firebase CLI)

```bash
firebase deploy --only firestore:rules
```

## What the Rules Do
- **Read**: Any authenticated user can read all reviews
- **Create**: Users can only create reviews with their own userId
- **Update**: Users can:
  - Update their own reviews completely
  - OR update only `reportCount` and `updatedAt` fields (for reporting) - this allows any user to report any review
- **Delete**: Users can only delete their own reviews
- **Reports subcollection**: Any authenticated user can create reports (for reporting details)

## If Still Not Working

1. **Check Authentication**: Make sure you're logged in as a regular user (not just admin)
2. **Check Rules Syntax**: The Firebase Console will show syntax errors if any
3. **Check Rules Deployment**: In Firebase Console, verify the rules are published (you'll see a timestamp)
4. **Wait a few seconds**: Rules can take 10-30 seconds to propagate


