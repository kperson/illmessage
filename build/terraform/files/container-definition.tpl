[
	{
		"environment": [
			{
				"name": "WAL_TABLE",
				"value": ${wal_table}
			},
			{
				"name": "DEAD_LETTER_TABLE",
				"value": ${dead_letter_table}
			},
			{
				"name": "SUBSCRIPTION_TABLE",
				"value": ${subscription_table}
			},
			{
				"name": "REGION",
				"value": ${region}
			},
			{
				"name": "TASK_SUBNET",
				"value": ${task_subnet}
			},
			{
				"name": "TASK_SECURITY_GROUP",
				"value": ${task_security_group}
			},
			{
				"name": "BACKGROUND_TASK_ARN",
				"value": "NA"
			}
		],
		"essential": true,
		"image": ${docker_image},
		"name": "illmessage-background",
		"logConfiguration": {
			"logDriver": "awslogs",
			"options": {
				"awslogs-group": ${log_group},
				"awslogs-region": ${region},
				"awslogs-stream-prefix": "illmessage-background"
			}
		}
	}
]