from flask import Flask, Response
from pathlib import Path


app = Flask(__name__)

@app.route("/diplomdaten", methods=["POST", "GET"])
def diplomdaten():
    return Response(Path("./responses/diplomdaten.xml").read_text(encoding="utf-8"), mimetype="text/xml; charset=utf-8")


@app.route("/testservice", methods=["POST", "GET"])
def testservice():
    return Response(Path("./responses/testservice.xml").read_text(encoding="utf-8"), mimetype="text/xml; charset=utf-8")

@app.route("/oneshared", methods=["POST", "GET"])
def oneshared_service():
    return Response(Path("./responses/oneshared.xml").read_text(encoding="utf-8"), mimetype="text/xml; charset=utf-8")

print(app.url_map)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080, debug=True)

