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
			}
		],
		"cpu": ${cpu},
		"essential": true,
		"image": ${docker_image},
		"memory": ${memory},
		"memoryReservation": ${memory},
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