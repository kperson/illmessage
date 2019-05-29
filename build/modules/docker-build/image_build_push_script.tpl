echo "Building $tag" \
&& docker build -f ${docker_file} -t ${tag} .
