/* read.c

*/

#include "syscall.h"

int main()
{
	
	int length = 25;
	char buffer[length];
	
	int i;
	for (i = 0; i < length; i++) {
		buffer[i] = 0;
	}
	
	Read(buffer,length,0); /*0 is for ConsoleInput*/
	Write(buffer,length,1);
	return 0;
}
