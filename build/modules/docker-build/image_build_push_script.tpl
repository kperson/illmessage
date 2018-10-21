echo "Building $tag" \
&& docker build -f ${docker_file} -t ${tag} . \
&& docker tag ${tag}:latest ${repo}:latest \
&& docker push ${repo}:latest