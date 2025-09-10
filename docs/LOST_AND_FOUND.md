# Finding Lost People: System Design

This document outlines the architecture, data flow, and implementation details for the 'Finding Lost People' feature in the Mahakumbh Crowd Safety app.

## 1. Feature Overview

The system is designed to facilitate the rapid reporting and discovery of missing persons during the Mahakumbh event. It connects visitors who have lost someone with a network of volunteers and other nearby app users.

### Core Components:

1.  **Report Submission (Visitors)**: A simple form for visitors to report a missing person, including essential details and a photo.
2.  **Broadcast and Notification System**: A backend service that processes reports and sends targeted alerts.
3.  **Live Map Integration**: A visual representation of the last known location of the missing person.
4.  **Case Management (Volunteers)**: Tools for volunteers to view detailed reports, coordinate searches, and resolve cases.

## 2. Data and Reporting Flow

1.  **Visitor Submits Report**: A visitor fills out the 'Report Missing Person' form with:
    *   **Reporter's Contact Info**: (Auto-filled, for volunteer use only).
    *   **Missing Person's Details**: Name, age, gender.
    *   **Appearance**: Clothing description, identifying marks.
    *   **Last Known Location**: Selected from a map or a list of zones.
    *   **Photo**: Uploaded from the device.

2.  **Backend Processing**: The report is sent to the backend, which saves it to a database and triggers the notification flow.

3.  **Notification Broadcast**: The system sends out two types of notifications:
    *   **Public Alert**: Sent to all visitors within a certain radius of the last known location. This alert contains masked, non-personally identifiable information.
    *   **Volunteer Alert**: Sent to all active volunteers. This alert contains the full, unmasked report details.

4.  **Volunteer Action**: Volunteers receive the detailed report and can view the last known location on their map. They can then begin a coordinated search.

5.  **Case Resolution**: When a volunteer finds the missing person, they use a 'Report Found Person' button in the app. This updates the case status to 'Resolved' and sends a notification to the original reporter.

## 3. Privacy and Security

Privacy is paramount. The system is designed with a two-tiered information access model:

*   **Public View**: Regular visitors will only see limited, anonymized information:
    *   **Photo**: The uploaded picture of the missing person.
    *   **General Description**: Age group (e.g., 'Child', 'Adult', 'Senior'), gender, and clothing color.
    *   **Last Seen Area**: Zone name (e.g., 'Sangam Zone'), not a precise pin.
    *   **Name and contact details are NEVER shown to the public.**

*   **Volunteer View**: Verified volunteers will have access to all submitted information to aid in the search:
    *   Full Name and Age.
    *   Detailed clothing and appearance description.
    *   Precise last known location on the map.
    *   The original reporter's contact information to facilitate communication.

## 4. AI-Powered Matching (Phase 2)

To enhance the system, an AI-powered photo recognition feature can be implemented in a future phase.

*   **How it Works**: When a 'found' person is reported by a volunteer (or at a help desk), their photo is taken. The system would use a facial recognition service (like Amazon Rekognition or a custom model) to compare this photo against the database of active missing person reports.
*   **Benefit**: This can automate the matching process, especially in cases where the found person cannot communicate their identity.
*   **Implementation**: This requires a secure backend service and careful handling of biometric data in compliance with privacy regulations.

## 5. Notification System

The notification system will be designed to be effective without being intrusive.

*   **Spam Prevention**: Users will only receive alerts for missing persons reported in their immediate vicinity. A rate-limiting mechanism on the backend will prevent a flood of notifications.
*   **Prioritization**: Reports for vulnerable individuals will be prioritized:
    *   **Children (e.g., age < 12) and Elderly (e.g., age > 65)** will trigger a high-priority alert.
    *   This alert will be accompanied by a **distinct, urgent audio tone** on the device to ensure it is immediately noticed by volunteers and nearby visitors.
*   **Smart Alerts**: Notifications will be actionable, allowing a user to tap to view the report details (with privacy controls) or dismiss it.
