# mentor-match

A system for fair and optimal matching of mentors with mentees. 

Given 
- a set of mentors, mentees, and where each mentee can have a list of mentor preferences

And
- each mentor can only be a mentor for one mentee (for now)

Then
- find all solutions options to matching mentees with their mentor preferences
- find an optimal solution (matching highest preferred mentors when possible)

# Quick start

## Prerequisites - running locally

You'll need to download Java and the Clojure programming language. You don't
need to understand Java or Clojure to use this tool - these are merely runtime
dependencies that need to be installed.

### 1. Java/JDK
You must have Java/JDK installed (e.g.
[adoptopenjdk.net](https://adoptopenjdk.net/)).

**NOTE**: If you are running OS X, you may be able to skip this step.

Ensure `java` command is on your path. In the OS X terminal, I set:

```bash
export JAVA_HOME=`/usr/libexec/java_home`
export PATH=$JAVA_HOME/bin:$PATH
```

### 2. Clojure CLI

You must also have [Clojure and Clojure CLI tools
installed](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

### 3. Clone this repo

Clone this repo into a local directory on your machine.

### 4. API access to Google sheets

You'll need API access to google sheets (data for mentors, mentees, and
preferences).

* Find the Google Sheets API Java Quickstart page ([currently
  here](https://developers.google.com/sheets/api/quickstart/java)).
* Complete ["Prerequisites"](https://developers.google.com/sheets/api/quickstart/java#prerequisites) (note: Gradle **not** needed) to generate `credentials.json`:

    * Enable the Google sheets API (and optionally the Google Drive API)
    * Click "Create credentials" in APIs & Services menu (Credentials). Create an Create OAuth client ID.
    * Add Google sheets scope: auth/spreadsheets.readonly (optionally Google Drive API scope ../auth/drive.file).
    * Just download and save JSON file.
    to your working directory.
    * Set the environment variable `GOOGLE_CREDENTIALS_JSON` to the path to
  `credentials.json`. For example, in a bash shell session:

```
      export GOOGLE_CREDENTIALS_JSON=`pwd`/client_secret_....json
```

## Test authorization

To test that your OAuth app is set up run

    $ clj -X:google-oauth2

You should see something like:

``` bash
Please open the following address in your browser:
  https://accounts.google.com/o/oauth2/auth?access_type=offline&client_id=10...
Attempting to open that address in the default browser now...
```

Authenticate using the same account that created the A Google Cloud Platform project.

Accept that app access and you should see "Received verification code. You may now close this window."

The terminal should now show

```bash
Token stored in:  ./tokens
```

You have successfully authenticated and can try the example below.

## Run an example

### The mentees, mentors, and preferences

The example uses a Google sheet:

* [Survey results as Google
  sheet](https://docs.google.com/spreadsheets/d/1TTwHRSfvTMjE_SCRKYT5LXzaBuki1duGNCaIOV_V2Ek/edit?usp=sharing).


Run the command (notice that you must use `'` and `"` to enclose the sheet URL): 

```bash
clj -X:match :sheet-url '"https://docs.google.com/spreadsheets/d/1TTwHRSfvTMjE_SCRKYT5LXzaBuki1duGNCaIOV_V2Ek/edit?usp=sharing"'
```

You'll now see the output:

```
From a total of 3 mentees.
With a total of 3 mentors.
Pre-matched mentors: 0
Available mentors: 3
Picking 3 mentees with preferences...

Running constraint solver...

Trying to match: 3 mentees
Trying  1 combinations...
0/1
{:score 165,
 :solution
 (["ricky@email.com" "@ethel"]
  ["mary@email.com" "@lucy"]
  ["bob@email.com" "@fred"])}
```

## License

Copyright © 2022 Karl Krukow (@krukow on GitHub)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.