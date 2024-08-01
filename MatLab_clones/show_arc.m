function show_arc(z,r,a1,a2)
% draw a circle arc of the circle
teta = linspace(a1,a2,50);
xco = real(z)+r*cos(teta); 	%x coordinates
yco = imag(z)+r*sin(teta);      	% y coordinates
plot(xco,yco);
end